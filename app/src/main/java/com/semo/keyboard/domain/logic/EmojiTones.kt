package com.semo.keyboard.domain.logic

/** Skin tone handling for emoji sequences. Ranges come from the Unicode Emoji_Modifier_Base property. */
internal object EmojiTones {

    private val baseRanges = intArrayOf(
        0x261D, 0x261D, 0x26F9, 0x26F9, 0x270A, 0x270D, 0x1F385, 0x1F385, 0x1F3C2, 0x1F3C4, 0x1F3C7, 0x1F3C7, 0x1F3CA, 0x1F3CC, 0x1F442, 0x1F443, 0x1F446, 0x1F450, 0x1F466, 0x1F478, 0x1F47C, 0x1F47C, 0x1F481, 0x1F483, 0x1F485, 0x1F487, 0x1F48F, 0x1F48F, 0x1F491, 0x1F491, 0x1F4AA, 0x1F4AA, 0x1F574, 0x1F575, 0x1F57A, 0x1F57A, 0x1F590, 0x1F590, 0x1F595, 0x1F596, 0x1F645, 0x1F647, 0x1F64B, 0x1F64F, 0x1F6A3, 0x1F6A3, 0x1F6B4, 0x1F6B6, 0x1F6C0, 0x1F6C0, 0x1F6CC, 0x1F6CC, 0x1F90C, 0x1F90C, 0x1F90F, 0x1F90F, 0x1F918, 0x1F91F, 0x1F926, 0x1F926, 0x1F930, 0x1F939, 0x1F93C, 0x1F93E, 0x1F977, 0x1F977, 0x1F9B5, 0x1F9B6, 0x1F9B8, 0x1F9B9, 0x1F9BB, 0x1F9BB, 0x1F9CD, 0x1F9CF, 0x1F9D1, 0x1F9DD, 0x1FAC3, 0x1FAC5, 0x1FAF0, 0x1FAF8
    )

    val tones = intArrayOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)

    private fun isTone(cp: Int) = cp in 0x1F3FB..0x1F3FF

    fun isModifierBase(cp: Int): Boolean {
        var i = 0
        while (i < baseRanges.size) {
            if (cp >= baseRanges[i] && cp <= baseRanges[i + 1]) return true
            i += 2
        }
        return false
    }

    /** The emoji without any skin tone, other characters (variation selectors, joiners) are kept. */
    fun plain(emoji: String): String {
        val sb = StringBuilder(emoji.length)
        emoji.codePoints().forEach { cp -> if (!isTone(cp)) sb.appendCodePoint(cp) }
        return sb.toString()
    }

    /** Stable key shared by every tone of the same emoji. */
    fun key(emoji: String): String {
        val sb = StringBuilder(emoji.length)
        emoji.codePoints().forEach { cp -> if (cp != 0xFE0F && !isTone(cp)) sb.appendCodePoint(cp) }
        return sb.toString()
    }

    /** One variant per tone: the tone follows every modifier base of the sequence. Empty when none applies. */
    fun variants(emoji: String): List<String> {
        val cps = plain(emoji).codePoints().toArray()
        if (cps.none { isModifierBase(it) }) return emptyList()
        return tones.map { tone ->
            val sb = StringBuilder()
            var i = 0
            while (i < cps.size) {
                val cp = cps[i]
                sb.appendCodePoint(cp)
                if (isModifierBase(cp)) {
                    sb.appendCodePoint(tone)
                    if (i + 1 < cps.size && cps[i + 1] == 0xFE0F) i++
                }
                i++
            }
            sb.toString()
        }
    }
}
