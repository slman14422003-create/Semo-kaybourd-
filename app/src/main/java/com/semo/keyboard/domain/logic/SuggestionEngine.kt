package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.Script
import kotlin.math.ln

/** إكمال الكلمات: كلمات المستخدم المتعلَّمة أولًا ثم القوائم المدمجة. منطق بحت بلا Android. */
object SuggestionEngine {

    /** الكلمة الجارية قبل المؤشر (حروف وفاصلة علوية فقط) */
    fun currentWord(before: String): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i).trimStart('\'', '’')
    }

    private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’'

    private fun scriptOf(c: Char): Script? = when {
        c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' -> Script.ARABIC
        c in '\u0400'..'\u04FF' -> Script.CYRILLIC
        c.isLetter() -> Script.LATIN
        else -> null
    }

    /** لغة الكلمة حسب نظام كتابة أول حرف فيها، وإلا نرجع للغة اللوحة الحالية */
    fun languageOf(word: String, fallback: KeyboardLanguage): KeyboardLanguage {
        val first = word.firstOrNull { it.isLetter() } ?: return fallback
        val script = scriptOf(first) ?: return fallback
        if (fallback.script == script) return fallback
        return when (script) {
            Script.ARABIC -> KeyboardLanguage.ARABIC
            Script.CYRILLIC -> KeyboardLanguage.RUSSIAN
            Script.LATIN -> KeyboardLanguage.ENGLISH
        }
    }

    fun starters(language: KeyboardLanguage): List<String> = WordLists.startersFor(language)

    private class Cand(val word: String, var score: Double)

    /**
     * إكمال الكلمة الجارية. كل مرشّح ياخذ درجة من عدة مصادر وتُجمع الدرجات: كلمات كتبتها بعد الكلمة السابقة
     * (ثلاثي ثم ثنائي) الأعلى، ثم نموذج الجمل بحسب احتمال الكلمة بهالسياق، ثم الكلمات المتعلَّمة، ثم القاموس
     * (الأشيع أولًا). بهذا "ar" بعد "how" تعطي "are" قبل "area"، و"ha" بعد "I" تعطي "have" قبل "hair".
     */
    fun clearCaches() {
        suggestCache.clear()
        correctionCache.clear()
        fixCache.clear()
    }

    private val suggestCache = LruCache<List<String>>(256)
    private val correctionCache = LruCache<List<String>>(128)
    private val fixCache = LruCache<String>(256)

    fun suggest(
        prefix: String,
        language: KeyboardLanguage,
        learned: List<String>,
        limit: Int = 3,
        context: String = "",
        bigrams: Map<String, List<String>> = emptyMap(),
        context2: String = ""
    ): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val key = "${language.name}|$prefix|$context|$context2|$limit|${ModelVersion.current()}|" +
            "${System.identityHashCode(learned)}|${learned.size}|${System.identityHashCode(bigrams)}|${bigrams.size}"
        suggestCache.get(key)?.let { return it }
        val result = suggestUncached(prefix, language, learned, limit, context, bigrams, context2)
        suggestCache.put(key, result)
        return result
    }

    private fun suggestUncached(
        prefix: String,
        language: KeyboardLanguage,
        learned: List<String>,
        limit: Int,
        context: String,
        bigrams: Map<String, List<String>>,
        context2: String
    ): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val pk = BigDictionary.key(prefix)
        if (pk.isEmpty()) return listOf(prefix)
        val p = prefix.lowercase()
        val cands = LinkedHashMap<String, Cand>()

        fun matches(w: String): Boolean {
            val k = BigDictionary.key(w)
            return k.startsWith(pk) && (k.length > pk.length || !w.lowercase().equals(p))
        }

        fun add(w: String, score: Double) {
            if (w.isEmpty() || !matches(w)) return
            val k = BigDictionary.key(w)
            val old = cands[k]
            if (old == null) cands[k] = Cand(w, score) else {
                // نفس الكلمة من مصدرين: نجمع الدرجتين (كل مصدر يؤكد الآخر) ونبقي الصيغة الأصلية الأولى
                old.score += score * 0.35
            }
        }

        // 1) ما تعلّمته اللوحة من كتابتك بعد الكلمة السابقة (أعلى أولوية)
        if (context.isNotEmpty()) {
            val ck = BigDictionary.key(context)
            if (context2.isNotEmpty()) {
                bigrams[BigDictionary.key(context2) + " " + ck]?.forEachIndexed { i, w -> add(w, 140.0 - i * 3) }
            }
            bigrams[ck]?.forEachIndexed { i, w -> add(w, 110.0 - i * 3) }
            // 2) نموذج الجمل: احتمال الكلمة بعد السياق
            for ((w, prob) in NgramModel.scored(language, context2, context)) add(w, 40.0 + prob * 70.0)
        } else {
            // بداية جملة: كلمات تبدأ بها الجمل عادة
            for (w in NgramModel.starters(language, 12)) add(w, 35.0)
        }

        // 3) كلمات المستخدم المتعلَّمة (الأكثر استعمالًا أولًا)
        var i = 0
        for (w in learned) {
            val sameScript = w.firstOrNull { it.isLetter() }?.let { scriptOf(it) == language.script } ?: false
            if (!sameScript) continue
            add(w, (60.0 - i * 0.6).coerceAtLeast(22.0))
            if (++i > 400) break
        }

        // 4) القائمة المدمجة الصغيرة (عربي/إنكليزي)
        val builtIn = WordLists.forLanguage(language)
        for ((j, w) in builtIn.withIndex()) add(w, (30.0 - j * 0.05).coerceAtLeast(18.0))

        // 5) القاموس الكبير: الأشيع أولًا بدرجة تنزل مع الرتبة
        for (w in BigDictionary.complete(prefix, language, 14)) {
            val rank = BigDictionary.rankOf(w, language)
            add(w, 24.0 - ln(rank.coerceAtLeast(0) + 2.0) * 1.4)
        }

        // 6) قائمة الكلمات الإنكليزية الصالحة (نحو 114 ألف) تكمّل لو لسا ما اكتملت الاقتراحات
        if (cands.size < limit && language == KeyboardLanguage.ENGLISH) {
            ValidWords.english?.withPrefix(p, limit)?.forEach { w -> add(w, 1.0) }
        }

        val result = ArrayList(cands.values.sortedByDescending { it.score }.take(limit).map { matchCase(prefix, it.word) })

        if (language.script == Script.ARABIC) {
            if (result.size < limit) {
                val clitic = ArabicText.clitic(prefix)
                if (clitic.isNotEmpty()) {
                    val stem = prefix.substring(clitic.length)
                    for (w in BigDictionary.complete(stem, language, limit)) {
                        if (result.size >= limit) break
                        val joined = clitic + w
                        if (result.none { BigDictionary.key(it) == BigDictionary.key(joined) }) result.add(joined)
                    }
                }
            }
            if (prefix.length >= 3 && prefix.endsWith('ه')) {
                val variant = prefix.dropLast(1) + "ة"
                val r = BigDictionary.rankOf(variant, language)
                if (r in 0 until COMMON_RANK && result.none { it == variant }) {
                    result.add(minOf(1, result.size), variant)
                    if (result.size > limit) result.removeAt(result.size - 1)
                }
            }
        }
        return if (result.isEmpty()) listOf(prefix) else result
    }

    // ---------------- التدقيق الإملائي ----------------

    private const val EN_ALPHABET = "abcdefghijklmnopqrstuvwxyz'"
    private val AR_ALPHABET: String = ('\u0621'..'\u064A').joinToString("")
    private val CYR_ALPHABET: String = ('\u0430'..'\u044F').joinToString("")

    /** حروف التعديل بحسب اللغة (الحروف الأساسية؛ التشكيل اللاتيني يُوحَّد بمفتاح البحث) */
    private fun alphabetOf(language: KeyboardLanguage): String = when (language.script) {
        Script.ARABIC -> if (language == KeyboardLanguage.PERSIAN) AR_ALPHABET + "پچژگکی" else AR_ALPHABET
        Script.CYRILLIC -> if (language == KeyboardLanguage.UKRAINIAN) CYR_ALPHABET + "іїєґ" else CYR_ALPHABET + "ё"
        Script.LATIN -> EN_ALPHABET + when (language) {
            KeyboardLanguage.TURKISH -> "ışğ"
            KeyboardLanguage.POLISH -> "ąćęłńóśźż"
            KeyboardLanguage.CZECH -> "áčďéěíňóřšťúůýž"
            KeyboardLanguage.ROMANIAN -> "ăâîșț"
            KeyboardLanguage.SWEDISH, KeyboardLanguage.FINNISH -> "åäö"
            KeyboardLanguage.DANISH, KeyboardLanguage.NORWEGIAN -> "æøå"
            KeyboardLanguage.HUNGARIAN -> "áéíóöőúüű"
            KeyboardLanguage.CATALAN -> "àçèéíïòóúü"
            else -> ""
        }
    }

    /** رتبة الكلمة الشائعة التي نسمح للاستبدال التلقائي بالتصحيح إليها (الأشيع فقط، حذرًا من تغيير كلمات سليمة) */
    private const val COMMON_RANK = 15_000
    private const val MIN_ARABIC_DICTIONARY = 3_000

    /** هل الكلمة معروفة: متعلَّمة، أو بالقاموس، أو بقائمة الكلمات الإنكليزية الصالحة */
    fun isKnown(word: String, language: KeyboardLanguage, learnedKeys: Set<String>): Boolean {
        val k = BigDictionary.key(word)
        if (k.isEmpty() || k in learnedKeys) return true
        if (BigDictionary.rankOf(k, language) >= 0) return true
        if (language == KeyboardLanguage.ENGLISH && ValidWords.english?.contains(k) == true) return true
        if (language.script == Script.ARABIC && k.length >= 4) {
            for (stem in ArabicText.stems(k)) {
                if (stem.length >= 3 && (stem in learnedKeys || BigDictionary.rankOf(stem, language) in 0 until 60_000)) return true
            }
        }
        return false
    }

    /** كل الكلمات التي تبعد عن [w] تعديلًا واحدًا: حذف، تبديل حرفين متجاورين، استبدال، أو إدخال حرف */
    private fun edits1(w: String, alphabet: String): Set<String> {
        val out = HashSet<String>()
        val n = w.length
        for (i in 0 until n) out.add(w.removeRange(i, i + 1))
        for (i in 0 until n - 1) out.add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
        for (i in 0 until n) for (c in alphabet) if (c != w[i]) out.add(w.substring(0, i) + c + w.substring(i + 1))
        for (i in 0..n) for (c in alphabet) out.add(w.substring(0, i) + c + w.substring(i))
        return out
    }

    /**
     * اقتراحات "هل تقصد": كلمات صحيحة تبعد تعديلًا واحدًا عن الكلمة غير المعروفة، الأشيع أولًا.
     * الكلمات التي تعلّمتها اللوحة تتقدّم، وتصحيح الحرف الأول أقل ترجيحًا لأن الخطأ فيه نادر.
     */
    fun corrections(
        word: String,
        language: KeyboardLanguage,
        learnedKeys: Set<String>,
        limit: Int = 2
    ): List<String> {
        if (word.length < 3 || word.length > 20) return emptyList()
        val key = "${language.name}|$word|$limit|${ModelVersion.current()}|${System.identityHashCode(learnedKeys)}|${learnedKeys.size}"
        correctionCache.get(key)?.let { return it }
        val result = correctionsUncached(word, language, learnedKeys, limit)
        correctionCache.put(key, result)
        return result
    }

    private fun correctionsUncached(
        word: String,
        language: KeyboardLanguage,
        learnedKeys: Set<String>,
        limit: Int
    ): List<String> {
        if (isKnown(word, language, learnedKeys)) return emptyList()
        val lower = word.lowercase()
        val alphabet = alphabetOf(language)
        val scored = ArrayList<Pair<Int, String>>()
        for (c in edits1(lower, alphabet)) {
            if (c.length < 2 || !c.any { it.isLetter() }) continue
            val rank = BigDictionary.rankOf(c, language)
            val valid = rank >= 0 || (language == KeyboardLanguage.ENGLISH && ValidWords.english?.contains(c) == true)
            if (!valid && BigDictionary.key(c) !in learnedKeys) continue
            var score = when {
                BigDictionary.key(c) in learnedKeys -> -1
                rank >= 0 -> rank
                else -> 500_000 + c.length
            }
            if (c.first() != lower.first()) score += 100_000
            // بالعربي نعرض الكلمة بصيغتها الأصلية (بالهمزة) من القاموس
            val shown = if (rank >= 0) BigDictionary.wordAt(rank, language) ?: c else c
            scored.add(score to shown)
        }
        return scored.sortedBy { it.first }.map { matchCase(word, it.second) }.distinct().take(limit)
    }

    /**
     * استبدال تلقائي حذر (كل اللغات المدعومة): يصحّح الكلمة غير المعروفة لكلمة شائعة واحدة واضحة
     * تبعد تعديلًا واحدًا (حذف/تبديل/استبدال/إدخال حرف). لا يلمس الكلمات التي فيها حروف كبيرة أو
     * رموز (أسماء، اختصارات) أو كلمات من أبجدية مختلفة، ولا يعمل لو الاختيار ملتبس (أكثر من مرشّح شائع بنفس القوة).
     * كان مقتصرًا على الإنكليزية فقط؛ صار يعمل بأي لغة حُمِّل قاموسها (عربي، فرنسي، إسباني...).
     */
    fun smartFix(word: String, language: KeyboardLanguage, learnedKeys: Set<String>): String? {
        if (word.length < 3 || word.length > 14) return null
        val key = "${language.name}|$word|${ModelVersion.current()}|${System.identityHashCode(learnedKeys)}|${learnedKeys.size}"
        fixCache.get(key)?.let { return it.ifEmpty { null } }
        val result = smartFixUncached(word, language, learnedKeys)
        fixCache.put(key, result ?: "")
        return result
    }

    private fun smartFixUncached(word: String, language: KeyboardLanguage, learnedKeys: Set<String>): String? {
        if (language.script == Script.ARABIC) {
            if (BigDictionary.size(language) < MIN_ARABIC_DICTIONARY) return null
            ArabicText.canonicalHamza(word, language)?.let { return it }
            ArabicText.endingFix(word, language)?.let { return it }
            val clitic = ArabicText.clitic(word)
            if (clitic.isNotEmpty() && !isKnown(word, language, learnedKeys)) {
                val stem = word.substring(clitic.length)
                val fixedStem = coreFix(stem, language, learnedKeys)
                if (fixedStem != null) return clitic + fixedStem
            }
        }
        return coreFix(word, language, learnedKeys)
    }

    private fun coreFix(word: String, language: KeyboardLanguage, learnedKeys: Set<String>): String? {
        if (word.length < 3 || word.length > 14) return null
        val alphabet = alphabetOf(language)
        if (!word.all { it in alphabet }) return null
        if (!BigDictionary.isLoaded(language) || isKnown(word, language, learnedKeys)) return null
        val ranked = edits1(word, alphabet)
            .filter { it.length >= 3 }
            .mapNotNull { c ->
                val r = BigDictionary.rankOf(c, language)
                if (r in 0 until COMMON_RANK) c to r else null
            }
            .sortedBy { it.second }
        if (ranked.isEmpty()) return null
        if (ranked.size >= 2 && ranked[1].second < ranked[0].second * 3) return null
        return ranked[0].first
    }

    private fun matchCase(prefix: String, word: String): String = when {
        prefix.length > 1 && prefix.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        prefix.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }
}
