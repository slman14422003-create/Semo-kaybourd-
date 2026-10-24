package com.semo.keyboard.domain.logic

interface PersonalRank {
    val version: Long
    fun boost(prefixKey: String, wordKey: String): Double
}

object InputProfile {
    const val GENERAL = "general"
    const val CHAT = "chat"
    const val MAIL = "mail"
    const val SEARCH = "search"

    private val chatHints = listOf(
        "whatsapp", "telegram", "messenger", "signal", "viber", "discord", "snapchat", "instagram",
        "messaging", "mms", "sms", "twitter", "threads", "wechat", "kakao", "line.android", "skype"
    )
    private val mailHints = listOf("mail", "outlook", "gm", "yahoo", "proton")
    private val searchHints = listOf("browser", "chrome", "firefox", "opera", "brave", "edge", "duckduckgo", "search", "vivaldi")

    fun classify(packageName: String?, searchField: Boolean): String {
        if (searchField) return SEARCH
        val p = packageName?.lowercase().orEmpty()
        if (p.isEmpty()) return GENERAL
        return when {
            chatHints.any { p.contains(it) } -> CHAT
            p.endsWith(".gm") || mailHints.any { it != "gm" && p.contains(it) } -> MAIL
            searchHints.any { p.contains(it) } -> SEARCH
            else -> GENERAL
        }
    }
}

object LearnFilter {
    private const val SCRIPT_ARABIC = 1
    private const val SCRIPT_LATIN = 2
    private const val SCRIPT_CYRILLIC = 3
    private const val SCRIPT_OTHER = 4

    private val arabicNameCues = setOf("\u064A\u0627", "\u0623\u0633\u062A\u0627\u0630", "\u0627\u0633\u062A\u0627\u0630", "\u0623\u0633\u062A\u0627\u0630\u0629", "\u0627\u0633\u062A\u0627\u0630\u0629", "\u062F\u0643\u062A\u0648\u0631", "\u062F\u0643\u062A\u0648\u0631\u0629", "\u0627\u0644\u062F\u0643\u062A\u0648\u0631", "\u0627\u0644\u062F\u0643\u062A\u0648\u0631\u0629", "\u0627\u0644\u0633\u064A\u062F", "\u0627\u0644\u0633\u064A\u062F\u0629", "\u0623\u0628\u0648", "\u0627\u0628\u0648", "\u0623\u0645", "\u0627\u0645", "\u0627\u0644\u0634\u064A\u062E", "\u0627\u0644\u0645\u0647\u0646\u062F\u0633", "\u0627\u0644\u0645\u0647\u0646\u062F\u0633\u0629", "\u0639\u0645", "\u062E\u0627\u0644", "\u062E\u0627\u0644\u062A\u064A", "\u0639\u0645\u062A\u064A", "\u0623\u062E\u064A", "\u0627\u062E\u064A", "\u0623\u062E\u062A\u064A", "\u0627\u062E\u062A\u064A")
    private val repeatedUnit = Regex("^(.{1,3})\\1{2,}$")
    private val sentenceEnd = charArrayOf('.', '!', '?', '\u061F', '\n')

    fun scriptCode(c: Char): Int = when {
        c.code in 0x0600..0x06FF || c.code in 0x0750..0x077F || c.code in 0xFB50..0xFDFF || c.code in 0xFE70..0xFEFF -> SCRIPT_ARABIC
        c in 'a'..'z' || c in 'A'..'Z' || c.code in 0x00C0..0x024F -> SCRIPT_LATIN
        c.code in 0x0400..0x04FF -> SCRIPT_CYRILLIC
        c.isLetter() -> SCRIPT_OTHER
        else -> 0
    }

    fun scriptOfWord(word: String): Int {
        for (c in word) {
            val s = scriptCode(c)
            if (s != 0) return s
        }
        return 0
    }

    fun acceptable(word: String): Boolean {
        if (word.length < 3 || word.length > 30) return false
        var script = 0
        for (c in word) {
            if (c == '\'' || c == '\u2019' || c == '-') continue
            if (!c.isLetter()) return false
            val s = scriptCode(c)
            if (script == 0) script = s else if (s != script) return false
        }
        if (script == 0) return false
        val lower = word.lowercase()
        var run = 1
        for (i in 1 until lower.length) {
            run = if (lower[i] == lower[i - 1]) run + 1 else 1
            if (run >= 3) return false
        }
        if (repeatedUnit.matches(lower)) return false
        if (lower.toSet().size < 2) return false
        return true
    }

    fun looksLikeName(word: String, before: String): Boolean {
        if (word.length < 3) return false
        val rest = before.dropLast(word.length).trimEnd(' ')
        if (rest.isEmpty()) return false
        return when (scriptOfWord(word)) {
            SCRIPT_LATIN -> {
                val first = word.first()
                first.isUpperCase() && word.drop(1).none { it.isUpperCase() } && rest.last() !in sentenceEnd
            }
            SCRIPT_ARABIC -> {
                val prev = rest.takeLastWhile { !it.isWhitespace() }
                prev in arabicNameCues
            }
            else -> false
        }
    }

    fun sameScript(candidate: String, reference: Int): Boolean {
        val s = scriptOfWord(candidate)
        return s == 0 || reference == 0 || s == reference
    }

    fun keepSameScript(list: List<String>, referenceWord: String?, fallbackScript: Int): List<String> {
        val ref = referenceWord?.let { scriptOfWord(it) }?.takeIf { it != 0 } ?: fallbackScript
        if (ref == 0) return list
        val kept = list.filter { sameScript(it, ref) }
        return if (kept.isEmpty()) list else kept
    }
}
