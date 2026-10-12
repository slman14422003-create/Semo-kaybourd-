package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage

/**
 * قاموس كبير (عشرات آلاف الكلمات مرتبة حسب الشيوع) يُحمَّل من ملفات على الجهاز تنزّلها شاشة الإعدادات،
 * ويُدمج مع القوائم المدمجة الصغيرة بالإكمال التلقائي وفك الكتابة بالسحب.
 *
 * للسرعة: فهرس مرتّب أبجديًا للبحث بالبادئة بالتنصيف (بدل المرور على كل الكلمات بكل ضغطة)، وفهرس
 * "هيكل الكلمة" مقسّم حسب (أول حرف، آخر حرف) لفك السحب. منطق بحت بلا Android.
 */
object BigDictionary {

    private class Index(
        /** الكلمات بترتيب الشيوع (الأولى = الأشيع) */
        val words: Array<String>,
        /** مفتاح البحث لكل كلمة (أحرف صغيرة + توحيد الهمزات) */
        val keys: Array<String>,
        /** أرقام الكلمات مرتبة أبجديًا حسب المفتاح */
        val order: IntArray,
        /** هيكل الكلمة لفك السحب (أحرف موحّدة بلا تكرار متتالٍ) */
        val skeletons: Array<String>,
        val buckets: HashMap<Int, IntArray>
    )

    /** فهرس لكل لغة محمَّلة (تُحمَّل اللغات المفعّلة فقط كي لا تتضخّم الذاكرة) */
    private val indices = java.util.concurrent.ConcurrentHashMap<KeyboardLanguage, Index>()

    private fun indexOf(language: KeyboardLanguage): Index? = indices[language]

    fun size(language: KeyboardLanguage): Int = indexOf(language)?.words?.size ?: 0

    fun isLoaded(language: KeyboardLanguage): Boolean = size(language) > 0

    /** ترتيب الكلمة بقائمة الشيوع (0 = الأشيع) أو -1 لو غير موجودة. الهمزات والحروف الكبيرة لا تؤثر */
    fun rankOf(word: String, language: KeyboardLanguage): Int {
        val idx = indexOf(language) ?: return -1
        val p = key(word)
        if (p.isEmpty()) return -1
        var lo = 0
        var hi = idx.order.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (idx.keys[idx.order[mid]] < p) lo = mid + 1 else hi = mid
        }
        // الترتيب مستقر: أول تطابق هو أصغر رقم (الأشيع) بين الكلمات المتطابقة المفتاح
        return if (lo < idx.order.size && idx.keys[idx.order[lo]] == p) idx.order[lo] else -1
    }

    /** الكلمة بصيغتها الأصلية (بالهمزات) عند ترتيبها [rank] */
    fun wordAt(rank: Int, language: KeyboardLanguage): String? = indexOf(language)?.words?.getOrNull(rank)

    fun clear(language: KeyboardLanguage) {
        indices.remove(language)
    }

    /** يبني الفهرس من قائمة مرتبة حسب الشيوع (ثقيل نسبيًا: يُستدعى بخيط خلفي) */
    fun setWords(language: KeyboardLanguage, words: List<String>) {
        indices[language] = build(words)
    }

    /**
     * توحيد الحرف لمفتاح البحث: أحرف صغيرة، توحيد الهمزات العربية والياء والكاف الفارسيتين،
     * وإزالة التشكيل اللاتيني (café ← cafe) كي تُقترح الكلمة بحروفها الصحيحة حتى لو كُتبت بلا تشكيل.
     */
    fun normChar(c: Char): Char = when (c) {
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ؤ' -> 'و'
        'ئ', 'ي' -> 'ی'
        'ك' -> 'ک'
        'à', 'á', 'â', 'ä', 'ã', 'å', 'ā', 'ą', 'À', 'Á', 'Â', 'Ä', 'Ã', 'Å', 'Ą' -> 'a'
        'ç', 'ć', 'č', 'Ç', 'Ć', 'Č' -> 'c'
        'è', 'é', 'ê', 'ë', 'ē', 'ę', 'ě', 'È', 'É', 'Ê', 'Ë', 'Ę' -> 'e'
        'ì', 'í', 'î', 'ï', 'Ì', 'Í', 'Î', 'Ï' -> 'i'
        'ñ', 'ń', 'Ñ', 'Ń' -> 'n'
        'ò', 'ó', 'ô', 'ö', 'õ', 'ø', 'ő', 'Ò', 'Ó', 'Ô', 'Ö', 'Õ', 'Ø' -> 'o'
        'ù', 'ú', 'û', 'ü', 'ů', 'Ù', 'Ú', 'Û', 'Ü' -> 'u'
        'ý', 'ÿ', 'Ý' -> 'y'
        'ś', 'š', 'Ś', 'Š' -> 's'
        'ź', 'ż', 'ž', 'Ź', 'Ż', 'Ž' -> 'z'
        'ł', 'Ł' -> 'l'
        'ё', 'Ё' -> 'е'
        'ґ', 'Ґ' -> 'г'
        'İ' -> 'i'
        else -> c.lowercaseChar()
    }

    /** مفتاح البحث: أحرف صغيرة + توحيد الهمزات الشائعة + حذف التشكيل والتطويل */
    fun key(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c in '\u064B'..'\u0652' || c == '\u0640') continue
            sb.append(normChar(c))
        }
        return sb.toString()
    }

    private fun skeleton(word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            if (!c.isLetter()) continue
            val n = normChar(c)
            if (sb.isEmpty() || sb[sb.length - 1] != n) sb.append(n)
        }
        return sb.toString()
    }

    private fun build(list: List<String>): Index {
        val words = list.toTypedArray()
        val n = words.size
        val keys = Array(n) { key(words[it]) }
        val order = (0 until n).sortedBy { keys[it] }.toIntArray()
        val skeletons = Array(n) { skeleton(words[it]) }
        val tmp = HashMap<Int, ArrayList<Int>>()
        for (i in 0 until n) {
            val sk = skeletons[i]
            if (sk.length < 2) continue
            val bucketKey = (sk.first().code shl 16) or sk.last().code
            tmp.getOrPut(bucketKey) { ArrayList() }.add(i)
        }
        val buckets = HashMap<Int, IntArray>(tmp.size * 2)
        for ((k, v) in tmp) buckets[k] = v.toIntArray()
        return Index(words, keys, order, skeletons, buckets)
    }

    /** أشيع [limit] كلمات تبدأ بـ [prefix] (وأطول منه) */
    fun complete(prefix: String, language: KeyboardLanguage, limit: Int): List<String> {
        val idx = indexOf(language) ?: return emptyList()
        val p = key(prefix)
        if (p.isEmpty() || limit <= 0) return emptyList()
        var lo = 0
        var hi = idx.order.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (idx.keys[idx.order[mid]] < p) lo = mid + 1 else hi = mid
        }
        val best = IntArray(limit)
        var found = 0
        var i = lo
        while (i < idx.order.size) {
            val rank = idx.order[i]
            val k = idx.keys[rank]
            if (!k.startsWith(p)) break
            if (k.length > p.length && (found < limit || rank < best[limit - 1])) {
                var j = if (found < limit) found else limit - 1
                while (j > 0 && best[j - 1] > rank) {
                    best[j] = best[j - 1]
                    j--
                }
                best[j] = rank
                if (found < limit) found++
            }
            i++
        }
        return List(found) { idx.words[best[it]] }
    }

    /** يمرّ على الكلمات التي هيكلها يبدأ بـ [first] وينتهي بـ [last] (مرشّحات فك السحب) */
    fun forEachSwipeCandidate(
        first: Char,
        last: Char,
        language: KeyboardLanguage,
        action: (word: String, skeleton: String, rank: Int) -> Unit
    ) {
        val idx = indexOf(language) ?: return
        val bucket = idx.buckets[(first.code shl 16) or last.code] ?: return
        for (i in bucket) action(idx.words[i], idx.skeletons[i], i)
    }
}
