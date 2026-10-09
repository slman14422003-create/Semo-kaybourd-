package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.ShiftState

/** فئة إيموجي: أيقونة التبويب + القائمة */
data class EmojiCategory(val icon: String, val emojis: List<String>)

/** يبني صفوف المفاتيح لكل صفحة ولغة وترتيب. منطق بحت بلا أي اعتماد على Android. */
object KeyboardLayoutProvider {

    // ---------- إنكليزي ----------
    private val qwerty = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )
    private val azerty = listOf(
        listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
        listOf("w", "x", "c", "v", "b", "n")
    )
    private val qwertz = listOf(
        listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("y", "x", "c", "v", "b", "n", "m")
    )

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
    private val arStandard = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        listOf("ذ", "د", "ز", "ر", "و", "ة", "ى", "ظ", "ء")
    )
    private val arAlphabetic = listOf(
        listOf("ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز"),
        listOf("س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ك"),
        listOf("ل", "م", "ن", "ه", "ة", "و", "ي", "ى", "ء")
    )

    private val arAlternates: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ء"),
        "و" to listOf("ؤ"),
        "ي" to listOf("ئ", "ى"),
        "ى" to listOf("ي", "ئ"),
        "ه" to listOf("ة"),
        "ء" to listOf("َ", "ُ", "ِ", "ّ", "ْ", "ً", "ٌ", "ٍ")
    )

    // ---------- رموز ----------
    private val latinDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val arabicDigitsList = listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠")
    private val sym1Row2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
    private val sym1Row3En = listOf(".", ",", "?", "!", "'")
    private val sym1Row3Ar = listOf(".", "،", "؟", "!", "'")
    private val sym2Row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val sym2Row2 = listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")

    val emojiCategories: List<EmojiCategory> = listOf(
        EmojiCategory(
            "😀", listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "😉",
                "😍", "🥰", "😘", "😗", "😋", "😛", "😜", "🤪", "🤗", "🤔", "🤨", "😐",
                "😑", "😶", "🙄", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖",
                "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵",
                "🥶", "😱", "😨", "😰", "😥", "😓", "🤤", "😴", "😎", "🤓", "🥳", "🤩"
            )
        ),
        EmojiCategory(
            "👍", listOf(
                "👍", "👎", "👌", "✌️", "🤞", "🤝", "👏", "🙌", "🙏", "💪", "👋", "🤚",
                "✋", "🖐️", "🤙", "👈", "👉", "👆", "👇", "☝️", "✊", "👊", "🤛", "🤜",
                "🤟", "🤘", "🫶", "🫡", "👀", "👁️", "👂", "👃", "👄", "🧠", "🦷", "🦴"
            )
        ),
        EmojiCategory(
            "❤️", listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
                "💞", "💓", "💗", "💖", "💘", "💝", "💯", "💢", "💥", "💫", "💦", "💨",
                "🔥", "✨", "⭐", "🌟", "🎉", "🎊", "🎁", "🏆"
            )
        ),
        EmojiCategory(
            "🍔", listOf(
                "🍕", "🍔", "🍟", "🌭", "🍿", "🥗", "🍝", "🍜", "🍣", "🍤", "🍙", "🍚",
                "🍞", "🥐", "🧀", "🥚", "🥓", "🍗", "🍖", "🌮", "🌯", "🥙", "🍎", "🍌",
                "🍇", "🍉", "🍓", "🍒", "🍑", "🥭", "🍍", "🥥", "☕", "🍵", "🥤", "🍰",
                "🎂", "🍩", "🍪", "🍫", "🍬", "🍭"
            )
        ),
        EmojiCategory(
            "🌹", listOf(
                "🌹", "🌸", "🌼", "🌻", "🌷", "🌳", "🌴", "🌵", "🍀", "🍁", "🌙", "☀️",
                "☁️", "🌧️", "⚡", "❄️", "🌈", "🌊", "🐶", "🐱", "🐭", "🐰", "🦊", "🐻",
                "🐼", "🦁", "🐯", "🐴", "🦄", "🐔", "🐧", "🐦", "🦋", "🐢", "🐟", "🐬"
            )
        ),
        EmojiCategory(
            "⚽", listOf(
                "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎱", "🏓", "🥊", "🎮", "🎯", "🎲",
                "🎵", "🎶", "🎤", "🎧", "🎸", "🎹", "🎬", "📷", "🚗", "🚕", "🚌", "✈️",
                "🚀", "🏠", "🏢", "📱", "💻", "⌚", "💡", "🔑", "🔒", "📚", "✏️", "📌",
                "✅", "❌", "⚠️", "❓", "❗", "➕", "➖", "🔔"
            )
        )
    )

    /** الأرقام المعروضة حسب لغة اللوحة وخيار الأرقام الهندية */
    private fun digitsFor(state: KeyboardUiState): List<String> =
        if (state.arabicDigits && state.language == KeyboardLanguage.ARABIC) arabicDigitsList else latinDigits

    /** حروف الصفوف الثلاثة لمعاينة الترتيب في الإعدادات */
    fun previewRows(
        language: KeyboardLanguage,
        english: EnglishLayout,
        arabic: ArabicLayout
    ): List<List<String>> =
        if (language == KeyboardLanguage.ARABIC) arabicLetterRows(arabic) else englishLetterRows(english)

    private fun englishLetterRows(layout: EnglishLayout): List<List<String>> = when (layout) {
        EnglishLayout.QWERTY -> qwerty
        EnglishLayout.AZERTY -> azerty
        EnglishLayout.QWERTZ -> qwertz
    }

    private fun arabicLetterRows(layout: ArabicLayout): List<List<String>> = when (layout) {
        ArabicLayout.STANDARD -> arStandard
        ArabicLayout.ALPHABETIC -> arAlphabetic
    }

    fun rows(state: KeyboardUiState): List<List<KeyDefinition>> = when (state.page) {
        KeyboardPage.LETTERS ->
            if (state.language == KeyboardLanguage.ARABIC) arabicRows(state) else englishRows(state)
        KeyboardPage.SYMBOLS_1 -> symbolRows(state, firstPage = true)
        KeyboardPage.SYMBOLS_2 -> symbolRows(state, firstPage = false)
        KeyboardPage.EMOJI, KeyboardPage.CLIPBOARD, KeyboardPage.EDIT -> emptyList()
    }

    /** الصف السفلي الإضافي (نمط iOS): إيموجي يسارًا وكرة أرضية يمينًا، بلا خلفية */
    fun utilityRow(): List<KeyDefinition> = listOf(
        KeyDefinition("😊", KeyAction.Emoji, weight = 1.3f, plain = true),
        KeyDefinition.spacer(7.4f),
        // ضغطة = تبديل اللغة (عربي/إنكليزي)، ضغطة مطوّلة = لوحة المفاتيح التالية بالنظام
        KeyDefinition("🌐", KeyAction.SwitchLanguage, weight = 1.3f, longPressAction = KeyAction.Globe, plain = true)
    )

    private fun numberRow(state: KeyboardUiState): List<List<KeyDefinition>> =
        if (state.numberRow) listOf(digitsFor(state).map { KeyDefinition(it, KeyAction.Character(it)) }) else emptyList()

    /** يوسّط صفًا أقصر من العرض الكامل بفراغات متساوية على الطرفين */
    private fun centered(row: List<KeyDefinition>, gap: Int): List<KeyDefinition> =
        if (gap <= 0) row else listOf(KeyDefinition.spacer(gap / 2f)) + row + listOf(KeyDefinition.spacer(gap / 2f))

    private fun englishRows(state: KeyboardUiState): List<List<KeyDefinition>> {
        val upper = state.shiftState != ShiftState.OFF
        fun mapRow(chars: List<String>) = chars.map { base ->
            val shown = if (upper) base.uppercase() else base
            val alts = enAlternates[base].orEmpty().map { if (upper) it.uppercase() else it }
            KeyDefinition(shown, KeyAction.Character(shown), longPressChars = alts)
        }
        val letters = englishLetterRows(state.englishLayout)
        val r1 = letters[0]
        val r2 = letters[1]
        val r3 = letters[2]

        // عرض الصف الكامل = 10 وحدات؛ مفتاحا Shift والحذف يأخذان ما تبقى من الصف الثالث
        val side = maxOf(1.5f, (10 - r3.size) / 2f)
        val shiftKey = KeyDefinition(
            label = if (state.shiftState == ShiftState.LOCKED) "⇪" else "⇧",
            action = KeyAction.Shift,
            weight = side,
            isAccent = state.shiftState != ShiftState.OFF
        )
        val row3 = listOf(shiftKey) + mapRow(r3) + listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = side))
        return numberRow(state) + listOf(
            mapRow(r1),
            centered(mapRow(r2), 10 - r2.size),
            row3,
            bottomRow(state, letters = true)
        ) + listOf(utilityRow())
    }

    private fun arabicRows(state: KeyboardUiState): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>) = chars.map { c ->
            KeyDefinition(c, KeyAction.Character(c), longPressChars = arAlternates[c].orEmpty())
        }
        val letters = arabicLetterRows(state.arabicLayout)
        val row3 = listOf(KeyDefinition.spacer(0.5f)) + mapRow(letters[2]) +
            listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f))
        return numberRow(state) + listOf(
            mapRow(letters[0]),
            mapRow(letters[1]),
            row3,
            bottomRow(state, letters = true)
        ) + listOf(utilityRow())
    }

    private fun symbolRows(state: KeyboardUiState, firstPage: Boolean): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>, weight: Float = 1f) =
            chars.map { KeyDefinition(it, KeyAction.Character(it), weight = weight) }

        val r1 = if (firstPage) digitsFor(state) else sym2Row1
        val r2 = if (firstPage) sym1Row2 else sym2Row2
        val r3 = if (state.language == KeyboardLanguage.ARABIC) sym1Row3Ar else sym1Row3En
        val switchKey = if (firstPage)
            KeyDefinition("#+=", KeyAction.SwitchToSymbols2, weight = 1.5f)
        else
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.5f)
        val third = listOf(switchKey) + mapRow(r3, 1.4f) + listOf(KeyDefinition("⌫", KeyAction.Backspace, weight = 1.5f))
        return listOf(mapRow(r1), mapRow(r2), third, bottomRow(state, letters = false), utilityRow())
    }

    private fun bottomRow(state: KeyboardUiState, letters: Boolean): List<KeyDefinition> {
        val arabic = state.language == KeyboardLanguage.ARABIC
        val ios26 = state.style == KeyboardStyle.IOS26
        val left = if (letters)
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.6f)
        else
            KeyDefinition(if (arabic) "أبج" else "ABC", KeyAction.SwitchToLetters, weight = 1.6f)

        // iOS 26: شريط المسافة بلا نص. iOS 18: عليه كلمة space / مسافة
        val spaceLabel = if (ios26) "" else if (arabic) "مسافة" else "space"
        return listOf(
            left,
            KeyDefinition(spaceLabel, KeyAction.Space, weight = 6.6f),
            enterKey(state.enterKind, arabic, ios26)
        )
    }

    private fun enterKey(kind: EnterKind, arabic: Boolean, ios26: Boolean): KeyDefinition {
        val text = when (kind) {
            EnterKind.RETURN -> if (arabic) "إدخال" else "return"
            EnterKind.SEARCH -> if (arabic) "بحث" else "Search"
            EnterKind.SEND -> if (arabic) "إرسال" else "Send"
            EnterKind.GO -> if (arabic) "اذهب" else "Go"
            EnterKind.DONE -> if (arabic) "تم" else "Done"
            EnterKind.NEXT -> if (arabic) "التالي" else "Next"
        }
        // مثل iOS: مفتاح return رمادي عادي، ويصير أزرق فقط لما يكون له إجراء (بحث/إرسال/اذهب...)
        // وبنمط iOS 26 يظهر سهم الإدخال بدل الكلمة للحالة العادية
        return KeyDefinition(
            label = text,
            action = KeyAction.Enter,
            weight = 1.8f,
            isAccent = kind != EnterKind.RETURN,
            textOnly = !(kind == EnterKind.RETURN && ios26)
        )
    }
}
