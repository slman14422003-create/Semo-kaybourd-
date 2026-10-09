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
        limit: Int = 3
    ): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val arabic = language == KeyboardLanguage.ARABIC
        val builtIn = if (arabic) WordLists.arabic else WordLists.english
        val p = prefix.lowercase()
        val out = LinkedHashSet<String>()

        for (w in learned) {
            if (out.size >= limit) break
            val sameScript = w.firstOrNull()?.let { isArabicChar(it) == arabic } ?: false
            if (sameScript && w.length > p.length && w.lowercase().startsWith(p)) out.add(w)
        }
        for (w in builtIn) {
            if (out.size >= limit) break
            if (w.length > p.length && w.lowercase().startsWith(p)) out.add(w)
        }

        val result = out.map { matchCase(prefix, it) }
        return if (result.isEmpty()) listOf(prefix) else result
    }

    private fun matchCase(prefix: String, word: String): String = when {
        prefix.length > 1 && prefix.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        prefix.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }
}
