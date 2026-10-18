package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage

object Arabizi {

    private val AR = KeyboardLanguage.ARABIC
    private const val BEAM = 90
    private const val DIGITS = "235789"

    private val single: Map<Char, List<String>> = mapOf(
        'a' to listOf("ا", "", "ى", "ة", "ع", "أ"),
        'b' to listOf("ب"),
        'c' to listOf("ك", "س", "ش"),
        'd' to listOf("د", "ض", "ذ"),
        'e' to listOf("", "ي", "ا", "ة", "ه"),
        'f' to listOf("ف"),
        'g' to listOf("غ", "ج", "ق"),
        'h' to listOf("ه", "ح", "ة"),
        'i' to listOf("ي", "", "ى"),
        'j' to listOf("ج"),
        'k' to listOf("ك", "خ"),
        'l' to listOf("ل"),
        'm' to listOf("م"),
        'n' to listOf("ن"),
        'o' to listOf("و", "", "ؤ"),
        'p' to listOf("ب"),
        'q' to listOf("ق", "ك"),
        'r' to listOf("ر"),
        's' to listOf("س", "ص"),
        't' to listOf("ت", "ط", "ة"),
        'u' to listOf("و", ""),
        'v' to listOf("ف"),
        'w' to listOf("و"),
        'x' to listOf("كس", "خ"),
        'y' to listOf("ي", "ى"),
        'z' to listOf("ز", "ظ", "ذ"),
        '2' to listOf("ء", "أ"),
        '3' to listOf("ع"),
        '4' to listOf("ش"),
        '5' to listOf("خ"),
        '6' to listOf("ط"),
        '7' to listOf("ح"),
        '8' to listOf("غ", "ق"),
        '9' to listOf("ق", "ص")
    )

    private val digraphs: Map<String, List<String>> = mapOf(
        "sh" to listOf("ش"),
        "kh" to listOf("خ"),
        "gh" to listOf("غ"),
        "th" to listOf("ث", "ذ", "ظ"),
        "dh" to listOf("ذ", "ض", "ظ"),
        "ch" to listOf("ش", "تش"),
        "ou" to listOf("و"),
        "oo" to listOf("و"),
        "ee" to listOf("ي"),
        "aa" to listOf("ا"),
        "ai" to listOf("ي", "اي"),
        "ei" to listOf("ي"),
        "3'" to listOf("غ"),
        "7'" to listOf("خ"),
        "'a" to listOf("ء", "أ"),
        "'e" to listOf("ء")
    )

    fun looksArabizi(word: String): Boolean {
        if (word.length < 3 || word.length > 18) return false
        if (!word.all { it.isLetterOrDigit() || it == '\'' }) return false
        if (word.all { it.isDigit() }) return false
        if (word.any { it.code > 0x24F && !it.isDigit() }) return false
        return word.any { it.isLetter() }
    }

    fun hasDigitSignal(word: String): Boolean =
        word.any { it in DIGITS } && word.any { it.isLetter() }

    private class Path(val text: String, val penalty: Int)

    fun trailingToken(before: String): String {
        var i = before.length
        while (i > 0) {
            val c = before[i - 1]
            if ((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '\'') i-- else break
        }
        val token = before.substring(i).trimStart { !it.isLetter() }
        return if (token.length in 3..18) token else ""
    }

    fun candidates(latin: String, limit: Int): List<String> {
        if (!BigDictionary.isLoaded(AR)) return emptyList()
        val w = latin.lowercase()
        val n = w.length
        val beams = Array(n + 1) { ArrayList<Path>() }
        beams[0].add(Path("", 0))
        for (i in 0 until n) {
            val cur = beams[i].sortedBy { it.penalty }.take(BEAM)
            if (cur.isEmpty()) continue
            val c = w[i]
            val singles: List<String>? = if (c == '\'') listOf("") else single[c]
            val dis: List<String>? = if (i + 1 < n) digraphs[w.substring(i, i + 2)] else null
            for (p in cur) {
                if (singles != null) {
                    for ((idx, s) in singles.withIndex()) {
                        if (s.isEmpty() && c != '\'' && (i == 0 || i == n - 1)) continue
                        val t = p.text + s
                        if (t.isNotEmpty() && !BigDictionary.hasPrefix(t, AR)) continue
                        beams[i + 1].add(Path(t, p.penalty + if (c == '\'') 0 else idx + 1))
                    }
                }
                if (dis != null) {
                    for ((idx, s) in dis.withIndex()) {
                        val t = p.text + s
                        if (!BigDictionary.hasPrefix(t, AR)) continue
                        beams[i + 2].add(Path(t, p.penalty + idx))
                    }
                }
            }
        }
        val found = LinkedHashMap<String, Double>()
        for (p in beams[n]) {
            if (p.text.length < 2) continue
            val r = BigDictionary.rankOf(p.text, AR)
            if (r < 0) continue
            val shown = BigDictionary.wordAt(r, AR) ?: continue
            val score = p.penalty * 4.0 + kotlin.math.ln(r + 2.0)
            val old = found[shown]
            if (old == null || old > score) found[shown] = score
        }
        return found.entries.sortedBy { it.value }.map { it.key }.take(limit)
    }
}
