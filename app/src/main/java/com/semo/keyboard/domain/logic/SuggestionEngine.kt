package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage

/** إكمال الكلمات: كلمات المستخدم المتعلَّمة أولًا ثم القوائم المدمجة. منطق بحت بلا Android. */
object SuggestionEngine {

    /** الكلمة الجارية قبل المؤشر (حروف وفاصلة علوية فقط) */
    fun currentWord(before: String): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i).trimStart('\'', '’')
    }

    private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’'

    private fun isArabicChar(c: Char): Boolean = c in '\u0600'..'\u06FF'

    /** لغة الكلمة حسب أول حرف فيها، وإلا نرجع للغة اللوحة الحالية */
    fun languageOf(word: String, fallback: KeyboardLanguage): KeyboardLanguage {
        val first = word.firstOrNull() ?: return fallback
        return if (isArabicChar(first)) KeyboardLanguage.ARABIC else KeyboardLanguage.ENGLISH
    }

    fun starters(language: KeyboardLanguage): List<String> =
        if (language == KeyboardLanguage.ARABIC) WordLists.arabicStarters else WordLists.englishStarters

    fun suggest(
        prefix: String,
        language: KeyboardLanguage,
        learned: List<String>,
        limit: Int = 3,
        context: String = "",
        bigrams: Map<String, List<String>> = emptyMap()
    ): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val arabic = language == KeyboardLanguage.ARABIC
        val builtIn = if (arabic) WordLists.arabic else WordLists.english
        val p = prefix.lowercase()
        val out = LinkedHashSet<String>()

        // الكلمات التي تعلّمتها اللوحة بعد الكلمة السابقة تتقدّم على غيرها
        if (context.isNotEmpty()) {
            bigrams[BigDictionary.key(context)]?.forEach { w ->
                if (out.size < limit && w.length > p.length && w.startsWith(p, ignoreCase = true)) out.add(w)
            }
        }

        for (w in learned) {
            if (out.size >= limit) break
            val sameScript = w.firstOrNull()?.let { isArabicChar(it) == arabic } ?: false
            if (sameScript && w.length > p.length && w.startsWith(p, ignoreCase = true)) out.add(w)
        }
        for (w in builtIn) {
            if (out.size >= limit) break
            if (w.length > p.length && w.startsWith(p, ignoreCase = true)) out.add(w)
        }
        // القاموس الكبير (إن نُزِّل من الإعدادات) يكمّل ما لم تغطّه القائمة المدمجة
        if (out.size < limit) {
            for (w in BigDictionary.complete(p, language, limit)) {
                if (out.size >= limit) break
                out.add(w)
            }
        }

        // قائمة الكلمات الإنكليزية الصالحة (نحو 114 ألف) تكمّل بالكلمات الأقصر لو لسا ما اكتملت الاقتراحات
        if (out.size < limit && !arabic) {
            ValidWords.english?.withPrefix(p, limit)?.forEach { w ->
                if (out.size < limit) out.add(w)
            }
        }

        val result = out.map { matchCase(prefix, it) }
        return if (result.isEmpty()) listOf(prefix) else result
    }

    // ---------------- التدقيق الإملائي ----------------

    private const val EN_ALPHABET = "abcdefghijklmnopqrstuvwxyz'"
    private val AR_ALPHABET: String = ('\u0621'..'\u064A').joinToString("")

    /** رتبة الكلمة الشائعة التي نسمح للاستبدال التلقائي بالتصحيح إليها (الأشيع فقط، حذرًا من تغيير كلمات سليمة) */
    private const val COMMON_RANK = 15_000

    /** هل الكلمة معروفة: متعلَّمة، أو بالقاموس، أو بقائمة الكلمات الإنكليزية الصالحة */
    fun isKnown(word: String, language: KeyboardLanguage, learnedKeys: Set<String>): Boolean {
        val k = BigDictionary.key(word)
        if (k.isEmpty() || k in learnedKeys) return true
        if (BigDictionary.rankOf(k, language) >= 0) return true
        if (language == KeyboardLanguage.ENGLISH && ValidWords.english?.contains(k) == true) return true
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
        if (isKnown(word, language, learnedKeys)) return emptyList()
        val lower = word.lowercase()
        val alphabet = if (language == KeyboardLanguage.ARABIC) AR_ALPHABET else EN_ALPHABET
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
     * استبدال تلقائي حذر (إنكليزي فقط): يصحّح الكلمة غير المعروفة لكلمة شائعة واحدة واضحة تبعد تعديلًا واحدًا.
     * لا يلمس الكلمات التي فيها حروف كبيرة أو رموز (أسماء، اختصارات)، ولا يعمل لو الاختيار ملتبس.
     */
    fun smartFix(word: String, language: KeyboardLanguage, learnedKeys: Set<String>): String? {
        if (language != KeyboardLanguage.ENGLISH || word.length < 4 || word.length > 14) return null
        if (!word.all { it in 'a'..'z' }) return null
        if (!BigDictionary.isLoaded(language) || isKnown(word, language, learnedKeys)) return null
        val ranked = edits1(word, EN_ALPHABET.dropLast(1))
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
