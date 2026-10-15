package com.semo.keyboard.domain.logic

/**
 * Keyword search over emoji. The index merges the suggestion dictionary with extra keywords and the category
 * titles, matches whole words first, then word prefixes, then substrings (three letters or more).
 */
object EmojiSearch {

    private val extra: List<Pair<String, String>> by lazy {
        buildList {
            fun kw(emoji: String, vararg words: String) {
                for (w in words) add(w to emoji)
            }
            kw("\uD83D\uDE00", "grin", "grinning", "face", "\u0648\u062C\u0647", "\u0627\u0628\u062A\u0633\u0627\u0645\u0629")
            kw("\uD83D\uDE02", "laugh", "laughing", "joy", "tears", "\u0636\u062D\u0643", "\u0647\u0647\u0647\u0647")
            kw("\uD83E\uDD23", "rofl", "rolling", "\u0627\u0646\u0642\u0644\u0627\u0628")
            kw("\uD83D\uDE09", "wink", "\u063A\u0645\u0632\u0629")
            kw("\uD83D\uDE0D", "heart eyes", "crush", "\u0639\u064A\u0648\u0646", "\u0627\u0639\u062C\u0627\u0628")
            kw("\uD83D\uDE0E", "sunglasses", "\u0646\u0638\u0627\u0631\u0629")
            kw("\uD83E\uDD70", "lovely", "adorable", "\u062D\u0646\u0627\u0646")
            kw("\uD83D\uDE2D", "weep", "bawling", "\u0628\u0643\u0627\u0621")
            kw("\uD83D\uDE21", "rage", "pout", "\u0639\u0635\u0628\u064A\u0629")
            kw("\uD83E\uDD2F", "mindblown", "explode", "\u0635\u062F\u0645\u0629")
            kw("\uD83E\uDD14", "wonder", "doubt", "\u062A\u0633\u0627\u0624\u0644")
            kw("\uD83D\uDE34", "zzz", "bed", "\u0646\u0627\u0626\u0645")
            kw("\uD83E\uDD2E", "vomit", "sick", "\u0642\u064A\u0621")
            kw("\uD83D\uDE37", "mask", "\u0643\u0645\u0627\u0645\u0629")
            kw("\uD83D\uDC4D", "like", "thumbs", "approve", "\u0627\u0639\u062C\u0628\u0646\u064A")
            kw("\uD83D\uDC4C", "perfect", "fine", "\u0645\u0645\u062A\u0627\u0632")
            kw("\u270C\uFE0F", "peace", "victory", "\u0646\u0635\u0631", "\u0633\u0644\u0627\u0645")
            kw("\uD83E\uDD1E", "luck", "fingers", "\u062D\u0638")
            kw("\uD83D\uDC4B", "wave", "\u062A\u062D\u064A\u0629")
            kw("\uD83D\uDE4F", "thank", "pray", "\u0634\u0643\u0631")
            kw("\uD83D\uDC40", "eyes", "look", "see", "\u0639\u064A\u0646", "\u0646\u0638\u0631")
            kw("\uD83D\uDCAF", "hundred", "score", "\u0645\u0626\u0629")
            kw("\u2764\uFE0F", "red heart", "\u0642\u0644\u0628 \u0627\u062D\u0645\u0631")
            kw("\uD83D\uDC99", "blue heart", "\u0642\u0644\u0628 \u0627\u0632\u0631\u0642")
            kw("\uD83D\uDC9A", "green heart", "\u0642\u0644\u0628 \u0627\u062E\u0636\u0631")
            kw("\uD83D\uDDA4", "black heart", "\u0642\u0644\u0628 \u0627\u0633\u0648\u062F")
            kw("\uD83C\uDF89", "tada", "confetti", "\u0627\u062D\u062A\u0641\u0627\u0644")
            kw("\uD83C\uDF81", "box", "\u0647\u062F\u064A\u0629")
            kw("\uD83C\uDFC6", "trophy", "winner", "champion", "\u0643\u0623\u0633", "\u0641\u0648\u0632")
            kw("\uD83D\uDC36", "doggy", "pet", "\u062D\u064A\u0648\u0627\u0646")
            kw("\uD83D\uDC31", "kitty", "meow", "\u0645\u064A\u0627\u0648")
            kw("\uD83D\uDC34", "horse", "\u062D\u0635\u0627\u0646")
            kw("\uD83E\uDD81", "lion", "\u0627\u0633\u062F")
            kw("\uD83D\uDC11", "sheep", "\u062E\u0631\u0648\u0641")
            kw("\uD83D\uDC2A", "camel", "\u062C\u0645\u0644")
            kw("\uD83C\uDF39", "roses", "\u0648\u0631\u062F\u0629")
            kw("\uD83C\uDF1E", "sunshine", "\u0636\u0648\u0621")
            kw("\uD83C\uDF08", "rainbow", "\u0642\u0648\u0633 \u0642\u0632\u062D")
            kw("\uD83C\uDF55", "slice", "\u0627\u0643\u0644")
            kw("\uD83C\uDF54", "fastfood", "\u0648\u062C\u0628\u0629")
            kw("\uD83C\uDF5F", "fries", "chips", "\u0628\u0637\u0627\u0637\u0627")
            kw("\uD83C\uDF70", "dessert", "sweet", "\u062D\u0644\u0648\u0649")
            kw("\uD83C\uDF4E", "fruit", "\u062A\u0641\u0627\u062D", "\u0641\u0627\u0643\u0647\u0629")
            kw("\uD83C\uDF4C", "banana", "\u0645\u0648\u0632")
            kw("\u2615", "cup", "drink", "\u0641\u0646\u062C\u0627\u0646")
            kw("\u26BD", "ball", "sport", "\u0631\u064A\u0627\u0636\u0629")
            kw("\uD83C\uDFC0", "basketball", "\u0633\u0644\u0629")
            kw("\uD83C\uDFAE", "game", "gaming", "controller", "\u0644\u0639\u0628\u0629")
            kw("\uD83D\uDE97", "auto", "vehicle", "\u0645\u0631\u0643\u0628\u0629")
            kw("\uD83D\uDE95", "taxi", "cab", "\u062A\u0627\u0643\u0633\u064A")
            kw("\uD83D\uDE8C", "bus", "\u0628\u0627\u0635", "\u062D\u0627\u0641\u0644\u0629")
            kw("\uD83D\uDE80", "rocket", "space", "\u0635\u0627\u0631\u0648\u062E")
            kw("\uD83D\uDCF1", "cell", "smartphone", "\u062C\u0647\u0627\u0632")
            kw("\uD83D\uDCBB", "pc", "\u062D\u0627\u0633\u0648\u0628")
            kw("\uD83D\uDCF7", "camera", "photo", "\u0643\u0627\u0645\u064A\u0631\u0627", "\u0635\u0648\u0631\u0629")
            kw("\uD83D\uDCA1", "idea", "bulb", "light", "\u0641\u0643\u0631\u0629")
            kw("\u23F0", "alarm", "clock", "time", "\u0633\u0627\u0639\u0629", "\u0648\u0642\u062A")
            kw("\u2705", "check", "done", "\u062A\u0645")
            kw("\u274C", "cross", "wrong", "\u062E\u0637\u0627")
            kw("\u26A0\uFE0F", "warning", "caution", "\u062A\u062D\u0630\u064A\u0631")
            kw("\uD83C\uDDF8\uD83C\uDDE6", "saudi", "\u0627\u0644\u0633\u0639\u0648\u062F\u064A\u0629")
            kw("\uD83C\uDDF8\uD83C\uDDFE", "syria", "\u0633\u0648\u0631\u064A\u0627")
            kw("\uD83C\uDDEA\uD83C\uDDEC", "egypt", "\u0645\u0635\u0631")
            kw("\uD83C\uDDEF\uD83C\uDDF4", "jordan", "\u0627\u0644\u0627\u0631\u062F\u0646")
            kw("\uD83C\uDDF1\uD83C\uDDE7", "lebanon", "\u0644\u0628\u0646\u0627\u0646")
            kw("\uD83C\uDDF5\uD83C\uDDF8", "palestine", "\u0641\u0644\u0633\u0637\u064A\u0646")
            kw("\uD83C\uDDEE\uD83C\uDDF6", "iraq", "\u0627\u0644\u0639\u0631\u0627\u0642")
        }
    }

    private val index: List<Pair<String, String>> by lazy {
        (EmojiSuggestions.entries().map { it.key to it.value } + extra)
            .map { normalize(it.first) to it.second }
            .distinct()
    }

    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw.trim().lowercase()) {
            when (c) {
                '\u0623', '\u0625', '\u0622' -> sb.append('\u0627')
                '\u0629' -> sb.append('\u0647')
                '\u0649' -> sb.append('\u064A')
                in '\u064B'..'\u065F' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private val nameIndex: List<Pair<String, String>> by lazy {
        val out = ArrayList<Pair<String, String>>()
        val english = java.util.Locale.ENGLISH
        val arabic = java.util.Locale("ar")
        for (category in KeyboardLayoutProvider.emojiCategories) {
            for (emoji in category.emojis) {
                val first = emoji.codePointAt(0)
                val count = emoji.codePointCount(0, emoji.length)
                if (first in 0x1F1E6..0x1F1FF && count == 2) {
                    val code = String(charArrayOf(
                        ('A' + (first - 0x1F1E6)),
                        ('A' + (emoji.codePointAt(Character.charCount(first)) - 0x1F1E6))
                    ))
                    val loc = java.util.Locale("", code)
                    out.add(normalize(loc.getDisplayCountry(english)) to emoji)
                    out.add(normalize(loc.getDisplayCountry(arabic)) to emoji)
                    out.add(code.lowercase() to emoji)
                } else if (emoji.replace("\uFE0F", "").let { it.codePointCount(0, it.length) } == 1) {
                    val name = runCatching { Character.getName(first) }.getOrNull() ?: continue
                    out.add(normalize(name) to emoji)
                }
            }
        }
        out
    }

    fun search(raw: String, limit: Int): List<String> {
        val q = normalize(raw)
        if (q.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for ((word, emoji) in index) if (word == q) out.add(emoji)
        for ((word, emoji) in index) if (word.startsWith(q)) out.add(emoji)
        for ((word, emoji) in index) {
            if (word.split(' ').any { it.startsWith(q) }) out.add(emoji)
        }
        if (q.length >= 3) for ((word, emoji) in index) if (word.contains(q)) out.add(emoji)
        if (out.size < limit) {
            for ((name, emoji) in nameIndex) {
                if (name.split(' ').any { it.startsWith(q) } || (q.length >= 3 && name.contains(q))) out.add(emoji)
                if (out.size >= limit) break
            }
        }
        if (out.size < limit) {
            for (category in EmojiData.categories) {
                if (normalize(category.titleEn).startsWith(q) || normalize(category.titleAr).startsWith(q)) {
                    out.addAll(category.emojis.take(limit))
                }
            }
        }
        return out.take(limit)
    }
}
