package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage
import java.util.concurrent.ConcurrentHashMap

/**
 * نموذج لغوي محلي (ثنائيات وثلاثيات كلمات) لتوقّع الكلمة التالية وتوليد تتمة الجملة.
 * يُبنى من جمل (مدمجة لكل من العربي والإنكليزي، أو منزَّلة من مكتبة اللغات) ويُخزَّن مضغوطًا على الجهاز.
 * المفاتيح بصيغة [BigDictionary.key]، والكلمات المتوقَّعة تُحفظ بصيغتها الأصلية (بالهمزات والتشكيل اللاتيني).
 * منطق بحت بلا Android.
 */
object NgramModel {

    /** نهاية الجملة: لو كانت الأرجح بعد الكلمة يتوقّف توليد التتمة */
    const val EOS = "</s>"

    /** مفتاح بداية الجملة بجدول الثنائيات (الكلمات التي تبدأ بها الجمل) */
    private const val BOS_KEY = "\u0002"

    /** الكلمات المتوقَّعة بعد سياق معيّن: مرتبة بالأكثر تكرارًا مع عدّاداتها */
    class Cont(val words: Array<String>, val counts: IntArray, val total: Int)

    class Model(val bi: HashMap<String, Cont>, val tri: HashMap<String, Cont>) {
        val isEmpty: Boolean get() = bi.isEmpty() && tri.isEmpty()
    }

    /** أرجح كلمة تالية مع نسبة ثقتها (0..1)، أو [eos] لو الأرجح انتهاء الجملة */
    class Best(val word: String, val confidence: Float, val eos: Boolean)

    private val models = ConcurrentHashMap<KeyboardLanguage, Model>()

    fun isLoaded(language: KeyboardLanguage): Boolean = models[language]?.isEmpty == false

    fun set(language: KeyboardLanguage, model: Model) {
        if (model.isEmpty) models.remove(language) else models[language] = model
    }

    fun clear(language: KeyboardLanguage) {
        models.remove(language)
    }

    // ------------------------------------------------------------------
    // التوقّع
    // ------------------------------------------------------------------

    private fun display(language: KeyboardLanguage, w: String): String {
        if (language == KeyboardLanguage.ENGLISH) {
            if (w == "i") return "I"
            if (w.startsWith("i'")) return "I" + w.substring(1)
        }
        return w
    }

    private fun contOf(model: Model, prev2: String, prev: String): Cont? {
        val k = BigDictionary.key(prev)
        if (k.isEmpty()) return null
        if (prev2.isNotEmpty()) {
            val k2 = BigDictionary.key(prev2)
            if (k2.isNotEmpty()) {
                val t = model.tri["$k2 $k"]
                if (t != null && t.total >= 2) return t
            }
        }
        return model.bi[k]
    }

    /** الكلمات الأرجح بعد [prev] (و[prev2] قبلها)، الثلاثيات أولًا ثم الثنائيات، بلا نهاية الجملة */
    fun next(language: KeyboardLanguage, prev2: String, prev: String, limit: Int): List<String> {
        val model = models[language] ?: return emptyList()
        val out = LinkedHashSet<String>()
        val k = BigDictionary.key(prev)
        if (k.isEmpty()) return emptyList()
        if (prev2.isNotEmpty()) {
            val k2 = BigDictionary.key(prev2)
            model.tri["$k2 $k"]?.words?.forEach { if (it != EOS && out.size < limit) out.add(display(language, it)) }
        }
        model.bi[k]?.words?.forEach { if (it != EOS && out.size < limit) out.add(display(language, it)) }
        return out.toList()
    }

    /** مرشّحات مع احتمالاتها لترتيب إكمال الكلمة الجارية حسب السياق (ثلاثي أثقل من ثنائي) */
    fun scored(language: KeyboardLanguage, prev2: String, prev: String): List<Pair<String, Float>> {
        val model = models[language] ?: return emptyList()
        val k = BigDictionary.key(prev)
        if (k.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, Float>>()
        if (prev2.isNotEmpty()) {
            val k2 = BigDictionary.key(prev2)
            val t = model.tri["$k2 $k"]
            if (t != null && t.total > 0) {
                for (i in t.words.indices) if (t.words[i] != EOS) out.add(display(language, t.words[i]) to (t.counts[i].toFloat() / t.total))
            }
        }
        val b = model.bi[k]
        if (b != null && b.total > 0) {
            for (i in b.words.indices) if (b.words[i] != EOS) out.add(display(language, b.words[i]) to (0.7f * b.counts[i].toFloat() / b.total))
        }
        return out
    }

    /** أرجح كلمة تالية بثقتها، لتوليد تتمة الجملة كلمة بكلمة */
    fun best(language: KeyboardLanguage, prev2: String, prev: String): Best? {
        val model = models[language] ?: return null
        val c = contOf(model, prev2, prev) ?: return null
        if (c.words.isEmpty() || c.total <= 0) return null
        val w = c.words[0]
        val conf = c.counts[0].toFloat() / c.total
        return Best(if (w == EOS) "" else display(language, w), conf, w == EOS)
    }

    /** الكلمات التي تبدأ بها الجمل غالبًا (للشريط عند بداية الجملة) */
    fun starters(language: KeyboardLanguage, limit: Int): List<String> {
        val model = models[language] ?: return emptyList()
        val c = model.bi[BOS_KEY] ?: return emptyList()
        val out = ArrayList<String>()
        for (w in c.words) {
            if (w == EOS) continue
            out.add(display(language, w).replaceFirstChar { it.uppercase() })
            if (out.size >= limit) break
        }
        return out
    }

    // ------------------------------------------------------------------
    // بناء النموذج من جمل
    // ------------------------------------------------------------------

    /** يقسّم نصًا لجمل من كلمات صغيرة (حروف وفاصلة علوية فقط؛ الأرقام والرموز تفصل الكلمات) */
    fun tokenize(text: String): List<List<String>> {
        val sentences = ArrayList<List<String>>()
        var current = ArrayList<String>()
        val sb = StringBuilder()
        fun flushWord() {
            if (sb.isNotEmpty()) {
                val t = sb.toString().trim('\'')
                if (t.isNotEmpty()) current.add(t)
                sb.setLength(0)
            }
        }
        fun endSentence() {
            flushWord()
            if (current.isNotEmpty()) {
                sentences.add(current)
                current = ArrayList()
            }
        }
        for (ch in text) {
            val c = if (ch == '’' || ch == '‘' || ch == 'ʼ') '\'' else ch
            when {
                c == '\u0640' || c in '\u064B'..'\u0652' -> Unit
                c.isLetter() || c == '\'' -> sb.append(c.lowercaseChar())
                c == '.' || c == '!' || c == '?' || c == '؟' || c == '…' || c == '\n' || c == '؛' -> endSentence()
                else -> flushWord()
            }
        }
        endSentence()
        return sentences
    }

    /**
     * يبني نموذجًا من [lines] (جملة بكل سطر). الكلمات التي تكرّرت [minWordFreq] مرات أو أكثر فقط تدخل المفردات
     * (حتى [vocabSize] كلمة)، والتراكيب التي تكرّرت [minCount] مرات أو أكثر فقط تُحفظ، وأقصى [topBi]/[topTri]
     * استمرارًا لكل سياق، فيبقى الملف والذاكرة صغيرين.
     */
    fun build(
        lines: List<String>,
        minWordFreq: Int,
        minCount: Int,
        vocabSize: Int = 24_000,
        topBi: Int = 8,
        topTri: Int = 5
    ): Model {
        // المرحلة 1: ترددات الكلمات لاختيار المفردات
        val freq = HashMap<String, Int>()
        for (line in lines) for (sentence in tokenize(line)) for (w in sentence) freq[w] = (freq[w] ?: 0) + 1
        val vocabWords = freq.entries
            .filter { it.value >= minWordFreq }
            .sortedByDescending { it.value }
            .take(vocabSize)
            .map { it.key }
        val ids = HashMap<String, Int>(vocabWords.size * 2)
        // 0 = بداية الجملة، 1 = نهايتها، والكلمات من 2
        val words = ArrayList<String>(vocabWords.size + 2)
        words.add(BOS_KEY)
        words.add(EOS)
        for (w in vocabWords) {
            ids[w] = words.size
            words.add(w)
        }

        // المرحلة 2: عدّ الثنائيات والثلاثيات
        val biCounter = LongCounter()
        val triCounter = LongCounter()
        val seq = IntArray(256)
        for (line in lines) {
            for (sentence in tokenize(line)) {
                var n = 0
                seq[n++] = 0
                for (w in sentence) {
                    if (n >= seq.size - 2) break
                    seq[n++] = ids[w] ?: -1
                }
                seq[n++] = 1
                for (i in 0 until n - 1) {
                    val a = seq[i]
                    val b = seq[i + 1]
                    if (a < 0 || b < 0) continue
                    biCounter.add((a.toLong() shl 15) or b.toLong())
                    if (i + 2 < n) {
                        val c = seq[i + 2]
                        // سياق ثلاثي يبدأ بكلمة حقيقية فقط (لا بداية جملة)
                        if (c >= 0 && a >= 2) {
                            triCounter.add((a.toLong() shl 30) or (b.toLong() shl 15) or c.toLong())
                        }
                    }
                }
            }
        }

        // المرحلة 3: تجميع الاستمرارات لكل سياق وقصّ الأفضل منها
        val biGroups = HashMap<Int, ArrayList<Long>>()
        biCounter.forEach { key, count ->
            if (count >= minCount) {
                val a = (key shr 15).toInt()
                val b = (key and 0x7FFFL).toInt()
                biGroups.getOrPut(a) { ArrayList() }.add((count.toLong() shl 32) or b.toLong())
            }
        }
        val triGroups = HashMap<Long, ArrayList<Long>>()
        triCounter.forEach { key, count ->
            if (count >= minCount) {
                val ctx = key shr 15
                val c = (key and 0x7FFFL).toInt()
                triGroups.getOrPut(ctx) { ArrayList() }.add((count.toLong() shl 32) or c.toLong())
            }
        }

        fun toCont(list: ArrayList<Long>, top: Int): Cont {
            list.sortDescending()
            var total = 0
            for (v in list) total += (v ushr 32).toInt()
            val size = minOf(top, list.size)
            val w = Array(size) { i -> words[(list[i] and 0xFFFFFFFFL).toInt()] }
            val c = IntArray(size) { i -> (list[i] ushr 32).toInt() }
            return Cont(w, c, total)
        }

        val bi = HashMap<String, Cont>(biGroups.size * 2)
        for ((a, list) in biGroups) {
            val keyStr = if (a == 0) BOS_KEY else BigDictionary.key(words[a])
            val cont = toCont(list, topBi)
            val existing = bi[keyStr]
            // كلمتان مختلفتان بنفس المفتاح (بهمزة وبدونها): نبقي الأكثر استعمالًا
            val existingTop = if (existing != null && existing.counts.isNotEmpty()) existing.counts[0] else -1
            if (existing == null || (cont.counts.isNotEmpty() && cont.counts[0] > existingTop)) {
                bi[keyStr] = cont
            }
        }
        val tri = HashMap<String, Cont>(triGroups.size * 2)
        for ((ctx, list) in triGroups) {
            val a = (ctx shr 15).toInt()
            val b = (ctx and 0x7FFFL).toInt()
            tri[BigDictionary.key(words[a]) + " " + BigDictionary.key(words[b])] = toCont(list, topTri)
        }
        return Model(bi, tri)
    }

    // ------------------------------------------------------------------
    // التخزين على الجهاز
    // ------------------------------------------------------------------

    /** سطر لكل سياق: B أو T، ثم السياق، ثم الكلمات بصيغة كلمة:عدّاد، مفصولة بتبويب */
    fun serialize(model: Model): String {
        val sb = StringBuilder()
        fun write(tag: String, map: HashMap<String, Cont>) {
            for ((ctx, c) in map) {
                sb.append(tag).append('\t').append(ctx).append('\t')
                for (i in c.words.indices) {
                    if (i > 0) sb.append(' ')
                    sb.append(c.words[i]).append(':').append(c.counts[i])
                }
                sb.append('\t').append(c.total).append('\n')
            }
        }
        write("B", model.bi)
        write("T", model.tri)
        return sb.toString()
    }

    fun parse(lines: Sequence<String>): Model {
        val bi = HashMap<String, Cont>()
        val tri = HashMap<String, Cont>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size < 4) continue
            val entries = parts[2].split(' ').filter { it.isNotEmpty() }
            val words = ArrayList<String>(entries.size)
            val counts = ArrayList<Int>(entries.size)
            for (e in entries) {
                val i = e.lastIndexOf(':')
                if (i <= 0) continue
                words.add(e.substring(0, i))
                counts.add(e.substring(i + 1).toIntOrNull() ?: 1)
            }
            if (words.isEmpty()) continue
            val cont = Cont(words.toTypedArray(), counts.toIntArray(), parts[3].toIntOrNull() ?: counts.sum())
            when (parts[0]) {
                "B" -> bi[parts[1]] = cont
                "T" -> tri[parts[1]] = cont
            }
        }
        return Model(bi, tri)
    }
}

/** عدّاد أعداد صحيحة طويلة بجدول تجزئة مفتوح بلا كائنات وسيطة (ملايين التراكيب بذاكرة قليلة) */
internal class LongCounter(initialCapacity: Int = 1 shl 16) {
    private var keys = LongArray(initialCapacity)
    private var vals = IntArray(initialCapacity)
    private var size = 0
    private var mask = initialCapacity - 1

    private fun slot(k: Long): Int {
        val h = k * -7046029254386353131L
        return ((h ushr 32).toInt() xor h.toInt()) and mask
    }

    private fun put(k: Long, v: Int) {
        var i = slot(k)
        while (true) {
            val cur = keys[i]
            if (cur == 0L) {
                keys[i] = k
                vals[i] = v
                size++
                return
            }
            if (cur == k) {
                vals[i] += v
                return
            }
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val oldKeys = keys
        val oldVals = vals
        keys = LongArray(oldKeys.size * 2)
        vals = IntArray(oldKeys.size * 2)
        mask = keys.size - 1
        size = 0
        for (j in oldKeys.indices) if (oldKeys[j] != 0L) put(oldKeys[j], oldVals[j])
    }

    /** يزيد عدّاد [key] واحدًا (المفتاح يُزاح +1 كي يبقى الصفر علامة الخانة الفارغة) */
    fun add(key: Long) {
        if (size * 2 >= keys.size) grow()
        put(key + 1L, 1)
    }

    fun forEach(action: (Long, Int) -> Unit) {
        for (j in keys.indices) if (keys[j] != 0L) action(keys[j] - 1L, vals[j])
    }
}
