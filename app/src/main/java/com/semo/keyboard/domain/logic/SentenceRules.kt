package com.semo.keyboard.domain.logic

import android.content.Context

object SentenceRules {

    private const val ASSET = "dict/ar_rules.txt"
    private const val MAX_TOKENS = 4

    @Volatile
    private var rules: Map<String, String> = emptyMap()

    private val repeatable: Set<String> = setOf(
        "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "is", "are", "for", "with", "at", "by", "it", "this", "my", "your",
        "في", "من", "على", "إلى", "الى", "عن", "أن", "ان", "إن", "مع", "هذا", "هذه", "التي", "الذي", "كان", "هل"
    )

    private val interrogativeAr = setOf("هل", "كيف", "لماذا", "متى", "أين", "اين", "ماذا", "كم", "ليش", "وين", "شو", "قديش", "إيمتى", "ايمتى", "علاش", "شنو")
    private val interrogativeEn = setOf("how", "what", "why", "where", "when", "who", "which", "whose")
    private val auxEn = setOf("do", "does", "did", "are", "is", "was", "were", "can", "could", "will", "would", "should", "have", "has")
    private val subjectEn = setOf("you", "i", "we", "they", "he", "she", "it", "there", "this", "that")
    private const val END_MARKS = ".!?؟…:;،,)\"'»”"

    fun attach(context: Context) {
        if (rules.isNotEmpty()) return
        val loaded = HashMap<String, String>(512)
        runCatching {
            context.applicationContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val right = line.substring(tab + 1).trim()
                    if (right.isNotEmpty()) loaded[line.substring(0, tab).trim()] = right
                }
            }
        }
        if (loaded.isNotEmpty()) rules = loaded
    }

    private fun splitEdges(token: String): Triple<String, String, String> {
        var s = 0
        var e = token.length
        while (s < e && !token[s].isLetter()) s++
        while (e > s && !token[e - 1].isLetter()) e--
        return Triple(token.substring(0, s), token.substring(s, e), token.substring(e))
    }

    fun apply(text: String): String {
        val table = rules
        if (table.isEmpty() || text.isEmpty()) return text
        val tokens = text.split(' ')
        val out = ArrayList<String>(tokens.size)
        var i = 0
        var changed = false
        while (i < tokens.size) {
            var hit: String? = null
            var used = 0
            var lead = ""
            var tail = ""
            for (n in minOf(MAX_TOKENS, tokens.size - i) downTo 1) {
                val first = splitEdges(tokens[i])
                val last = splitEdges(tokens[i + n - 1])
                if (first.second.isEmpty() || last.second.isEmpty()) continue
                if (n > 1) {
                    var inner = true
                    for (k in i until i + n - 1) if (splitEdges(tokens[k]).third.isNotEmpty()) inner = false
                    if (!inner) continue
                }
                val core = if (n == 1) first.second else buildString {
                    append(first.second)
                    for (k in i + 1 until i + n - 1) append(' ').append(tokens[k])
                    append(' ').append(last.second)
                }
                val h = table[core] ?: continue
                hit = h
                used = n
                lead = first.first
                tail = last.third
                break
            }
            if (hit != null) {
                out.add(lead + hit + tail)
                i += used
                changed = true
            } else {
                out.add(tokens[i])
                i++
            }
        }
        return if (changed) out.joinToString(" ") else text
    }

    fun dedupe(text: String): String {
        val tokens = text.split(' ')
        if (tokens.size < 2) return text
        val out = ArrayList<String>(tokens.size)
        var changed = false
        for (t in tokens) {
            val core = splitEdges(t).second.lowercase()
            val prev = out.lastOrNull()
            if (prev != null && core.isNotEmpty() && splitEdges(prev).second.lowercase() == core && core in repeatable && splitEdges(prev).third.isEmpty()) {
                changed = true
                continue
            }
            out.add(t)
        }
        return if (changed) out.joinToString(" ") else text
    }

    fun finishQuestion(segment: String): String {
        val body = segment.trimEnd()
        if (body.length < 5) return segment
        if (body.last() in END_MARKS) return segment
        val words = body.split(' ').filter { it.isNotEmpty() }
        if (words.size < 2) return segment
        val first = words[0].lowercase()
        val arabic = ArabicText.isArabicWord(words[0])
        val second = words[1].lowercase()
        val isQuestion = if (arabic) first in interrogativeAr else
            (first in interrogativeEn && words.size >= 3 && second != "a" && second != "an") ||
                (first in auxEn && second in subjectEn && words.size >= 3)
        if (!isQuestion) return segment
        val mark = if (arabic) "\u061F" else "?"
        return body + mark + segment.substring(body.length)
    }

    fun spacing(text: String): String {
        var out = text
        out = AFTER_ARABIC_MARK.replace(out, "$1 ")
        out = AFTER_LATIN_MARK.replace(out, "$1 ")
        out = DOUBLE_ARABIC_COMMA.replace(out, "\u060C")
        return out
    }

    private val AFTER_ARABIC_MARK = Regex("([\u060C\u061B\u061F])(?=\\p{L})")
    private val AFTER_LATIN_MARK = Regex("(?<=\\p{L})([,!?])(?=\\p{L})")
    private val DOUBLE_ARABIC_COMMA = Regex("\u060C{2,}")
}
