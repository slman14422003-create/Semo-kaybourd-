package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.ShiftState

/** يبني صفوف المفاتيح لكل صفحة ولغة. منطق بحت بلا أي اعتماد على Android. */
object KeyboardLayoutProvider {

    // ---------- إنكليزي ----------
    private val enRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val enRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val enRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    private val enAlternates: Map<String, List<String>> = mapOf(
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

    // ---------- عربي ----------
    private val arRow1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج")
    private val arRow2 = listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط")
    private val arRow3 = listOf("ذ", "د", "ز", "ر", "و", "ة", "ى", "ظ", "ء")

    private val arAlternates: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ء"),
        "و" to listOf("ؤ"),
        "ي" to listOf("ئ", "ى"),
        "ى" to listOf("ي", "ئ"),
        "ه" to listOf("ة"),
        "ء" to listOf("َ", "ُ", "ِ", "ّ", "ْ", "ً", "ٌ", "ٍ")
    )

    // ---------- رموز ----------
    private val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val sym1Row2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
    private val sym1Row3En = listOf(".", ",", "?", "!", "'")
    private val sym1Row3Ar = listOf(".", "،", "؟", "!", "'")
    private val sym2Row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val sym2Row2 = listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")

    val emojis: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "😉",
        "😍", "🥰", "😘", "😗", "😋", "😛", "😜", "🤪", "🤗", "🤔", "🤨", "😐",
        "😑", "😶", "🙄", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖",
        "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵",
        "🥶", "😱", "😨", "😰", "😥", "😓", "🤤", "😴", "😎", "🤓", "🥳", "🤩",
        "👍", "👎", "👌", "✌️", "🤞", "🤝", "👏", "🙌", "🙏", "💪", "👋", "🤚",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕", "💖", "💯",
        "🔥", "✨", "⭐", "🌟", "🎉", "🎊", "🎁", "🏆", "⚽", "🎮", "🎵", "🎶",
        "🌹", "🌸", "🌙", "☀️", "☁️", "🌧️", "⚡", "❄️", "🍕", "🍔", "☕", "🍰",
        "🚗", "✈️", "🏠", "📱", "💻", "📷", "💡", "🔒", "✅", "❌", "⚠️", "❓"
    )

    fun rows(state: KeyboardUiState): List<List<KeyDefinition>> = when (state.page) {
        KeyboardPage.LETTERS ->
            if (state.language == KeyboardLanguage.ARABIC) arabicRows(state) else englishRows(state)
        KeyboardPage.SYMBOLS_1 -> symbolRows(state, firstPage = true)
        KeyboardPage.SYMBOLS_2 -> symbolRows(state, firstPage = false)
        KeyboardPage.EMOJI -> emptyList()
    }

    /** الصف السفلي لصفحة الإيموجي (ABC + حذف) */
    fun emojiBottomRow(): List<KeyDefinition> = listOf(
        KeyDefinition("ABC", KeyAction.SwitchToLetters, weight = 2f),
        KeyDefinition.spacer(6f),
        KeyDefinition("⌫", KeyAction.Backspace, weight = 2f)
    )

    private fun numberRow(state: KeyboardUiState): List<List<KeyDefinition>> =
        if (state.numberRow) listOf(digits.map { KeyDefinition(it, KeyAction.Character(it)) }) else emptyList()

    private fun englishRows(state: KeyboardUiState): List<List<KeyDefinition>> {
        val upper = state.shiftState != ShiftState.OFF
        fun mapRow(chars: List<String>) = chars.map { base ->
            val shown = if (upper) base.uppercase() else base
            val alts = enAlternates[base].orEmpty().map { if (upper) it.uppercase() else it }
            KeyDefinition(shown, KeyAction.Character(shown), longPressChars = alts)
        }
        val shiftKey = KeyDefinition(
            label = if (state.shiftState == ShiftState.LOCKED) "⇪" else "⇧",
            action = KeyAction.Shift,
            weight = 1.5f,
            isAccent = state.shiftState != ShiftState.OFF
        )
        val row2 = listOf(KeyDefinition.spacer(0.5f)) + mapRow(enRow2) + listOf(KeyDefinition.spacer(0.5f))
        val row3 = listOf(shiftKey) + mapRow(enRow3) + listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f))
        return numberRow(state) + listOf(mapRow(enRow1), row2, row3, bottomRow(state, letters = true))
    }

    private fun arabicRows(state: KeyboardUiState): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>) = chars.map { c ->
            KeyDefinition(c, KeyAction.Character(c), longPressChars = arAlternates[c].orEmpty())
        }
        val row3 = mapRow(arRow3) + listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f))
        return numberRow(state) + listOf(mapRow(arRow1), mapRow(arRow2), listOf(KeyDefinition.spacer(0.5f)) + row3, bottomRow(state, letters = true))
    }

    private fun symbolRows(state: KeyboardUiState, firstPage: Boolean): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>, weight: Float = 1f) =
            chars.map { KeyDefinition(it, KeyAction.Character(it), weight = weight) }

        val r1 = if (firstPage) digits else sym2Row1
        val r2 = if (firstPage) sym1Row2 else sym2Row2
        val r3 = if (state.language == KeyboardLanguage.ARABIC) sym1Row3Ar else sym1Row3En
        val switchKey = if (firstPage)
            KeyDefinition("#+=", KeyAction.SwitchToSymbols2, weight = 1.5f)
        else
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.5f)
        val third = listOf(switchKey) + mapRow(r3, 1.4f) + listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f))
        return listOf(mapRow(r1), mapRow(r2), third, bottomRow(state, letters = false))
    }

    private fun bottomRow(state: KeyboardUiState, letters: Boolean): List<KeyDefinition> {
        val arabic = state.language == KeyboardLanguage.ARABIC
        val left = if (letters)
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.5f)
        else
            KeyDefinition(if (arabic) "أبج" else "ABC", KeyAction.SwitchToLetters, weight = 1.5f)

        // الكرة الأرضية: ضغطة = تبديل اللغة (عربي/إنكليزي)، ضغطة مطوّلة = لوحة المفاتيح التالية بالنظام
        val globe = if (letters)
            KeyDefinition("🌐", KeyAction.SwitchLanguage, weight = 1.2f, longPressAction = KeyAction.Globe)
        else
            KeyDefinition("🌐", KeyAction.Globe, weight = 1.2f)

        return listOf(
            left,
            KeyDefinition("😊", KeyAction.Emoji, weight = 1.2f),
            globe,
            KeyDefinition(if (arabic) "مسافة" else "space", KeyAction.Space, weight = 4.2f),
            enterKey(state.enterKind, arabic)
        )
    }

    private fun enterKey(kind: EnterKind, arabic: Boolean): KeyDefinition {
        val text = when (kind) {
            EnterKind.RETURN -> if (arabic) "إدخال" else "return"
            EnterKind.SEARCH -> if (arabic) "بحث" else "Search"
            EnterKind.SEND -> if (arabic) "إرسال" else "Send"
            EnterKind.GO -> if (arabic) "اذهب" else "Go"
            EnterKind.DONE -> if (arabic) "تم" else "Done"
            EnterKind.NEXT -> if (arabic) "التالي" else "Next"
        }
        // مثل iOS: مفتاح return رمادي عادي، ويصير أزرق فقط لما يكون له إجراء (بحث/إرسال/اذهب...)
        return KeyDefinition(
            label = text,
            action = KeyAction.Enter,
            weight = 2f,
            isAccent = kind != EnterKind.RETURN,
            textOnly = true
        )
    }
}
