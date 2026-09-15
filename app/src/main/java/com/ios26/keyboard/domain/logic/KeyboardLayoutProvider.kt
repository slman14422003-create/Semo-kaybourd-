package com.ios26.keyboard.domain.logic

import com.ios26.keyboard.domain.model.KeyAction
import com.ios26.keyboard.domain.model.KeyDefinition
import com.ios26.keyboard.domain.model.KeyboardPage
import com.ios26.keyboard.domain.model.ShiftState

/**
 * يبني صفوف المفاتيح لكل صفحة من صفحات اللوحة، مطابقةً لشكل لوحة iOS:
 * 10 حروف بالصف الأول، 9 بالثاني مع إزاحة، والثالث بمفتاحي Shift وBackspace بالأطراف،
 * وصف أخير بمفاتيح التبديل والمسافة والإدخال.
 */
object KeyboardLayoutProvider {

    private val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

    /** بدائل الضغط المطوّل لكل حرف (نفس فكرة لوحة iOS: امسك الحرف ليطلع صف بدائله) */
    private val longPressAlternates: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å"),
        "e" to listOf("è", "é", "ê", "ë"),
        "i" to listOf("ì", "í", "î", "ï"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø"),
        "u" to listOf("ù", "ú", "û", "ü"),
        "s" to listOf("ß", "ś", "š"),
        "c" to listOf("ç", "ć"),
        "n" to listOf("ñ", "ń"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł")
    )

    private val symbols1Row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val symbols1Row2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
    private val symbols1Row3 = listOf(".", ",", "?", "!", "'")

    private val symbols2Row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val symbols2Row2 = listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")
    private val symbols2Row3 = listOf(".", ",", "?", "!", "'")

    private val emojiRow1 = listOf("😀", "😂", "😍", "😎", "🤔", "😢", "😡", "👍", "🙏", "🔥")
    private val emojiRow2 = listOf("❤️", "🎉", "✨", "🥳", "😴", "🤗", "😅", "🙌", "💯")
    private val emojiRow3 = listOf("😇", "🤩", "🥺", "😭", "🤤", "😏", "🫶")

    fun rows(page: KeyboardPage, shiftState: ShiftState): List<List<KeyDefinition>> {
        return when (page) {
            KeyboardPage.LETTERS -> lettersRows(shiftState)
            KeyboardPage.SYMBOLS_1 -> symbolRows(symbols1Row1, symbols1Row2, symbols1Row3, isFirstPage = true)
            KeyboardPage.SYMBOLS_2 -> symbolRows(symbols2Row1, symbols2Row2, symbols2Row3, isFirstPage = false)
            KeyboardPage.EMOJI -> emojiRows()
        }
    }

    private fun lettersRows(shiftState: ShiftState): List<List<KeyDefinition>> {
        val upper = shiftState != ShiftState.OFF
        fun mapRow(chars: List<String>) = chars.map { base ->
            val shown = if (upper) base.uppercase() else base
            val alternates = longPressAlternates[base]?.map { if (upper) it.uppercase() else it } ?: emptyList()
            KeyDefinition(shown, KeyAction.Character(shown), longPressChars = alternates)
        }

        val shiftKey = KeyDefinition(
            label = if (shiftState == ShiftState.LOCKED) "⇪" else "⇧",
            action = KeyAction.Shift,
            weight = 1.5f,
            isAccent = shiftState != ShiftState.OFF
        )
        val backspaceKey = KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f)

        val thirdRow = listOf(shiftKey) + mapRow(row3) + listOf(backspaceKey)

        val bottomRow = listOf(
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.5f),
            KeyDefinition("🌐", KeyAction.Globe, weight = 1.2f),
            KeyDefinition("😊", KeyAction.Emoji, weight = 1.2f),
            KeyDefinition("مسافة", KeyAction.Space, weight = 4f),
            KeyDefinition("إدخال", KeyAction.Enter, weight = 1.8f, isAccent = true)
        )

        return listOf(mapRow(row1), mapRow(row2), thirdRow, bottomRow)
    }

    private fun symbolRows(
        r1: List<String>, r2: List<String>, r3: List<String>, isFirstPage: Boolean
    ): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>) = chars.map { KeyDefinition(it, KeyAction.Character(it)) }

        val switchKey = if (isFirstPage)
            KeyDefinition("#+=", KeyAction.SwitchToSymbols2, weight = 1.5f)
        else
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.5f)

        val thirdRow = listOf(switchKey) + mapRow(r3) + listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f))

        val bottomRow = listOf(
            KeyDefinition("ABC", KeyAction.SwitchToLetters, weight = 1.5f),
            KeyDefinition("🌐", KeyAction.Globe, weight = 1.2f),
            KeyDefinition("😊", KeyAction.Emoji, weight = 1.2f),
            KeyDefinition("مسافة", KeyAction.Space, weight = 4f),
            KeyDefinition("إدخال", KeyAction.Enter, weight = 1.8f, isAccent = true)
        )

        return listOf(mapRow(r1), mapRow(r2), thirdRow, bottomRow)
    }

    private fun emojiRows(): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>) = chars.map { KeyDefinition(it, KeyAction.Character(it)) }
        val bottomRow = listOf(
            KeyDefinition("ABC", KeyAction.SwitchToLetters, weight = 2f),
            KeyDefinition("⌫", KeyAction.Backspace, weight = 2f)
        )
        return listOf(mapRow(emojiRow1), mapRow(emojiRow2), mapRow(emojiRow3), bottomRow)
    }
}
