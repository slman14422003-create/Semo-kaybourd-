package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.FieldKind
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.ShiftState

/** فئة إيموجي: أيقونة التبويب + القائمة */
data class EmojiCategory(
    val icon: String,
    val emojis: List<String>,
    val titleAr: String = "",
    val titleEn: String = ""
)

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

    /**
     * عربي QWERTY (صوتي): كل حرف عربي بمكان الحرف الإنكليزي الأقرب لصوته،
     * ق=Q و=W ع=E ر=R ت=T ي=Y ... س=S د=D ف=F غ=G ه=H ج=J ك=K ل=L ز=Z خ=X ث=C ط=V ب=B ن=N م=M.
     * الباقي (ة ى ء أ إ آ ؤ ئ) بالضغط المطوّل على أقرب حرف.
     */
    private val arQwerty = listOf(
        listOf("ق", "و", "ع", "ر", "ت", "ي", "ص", "ح", "ض", "ظ"),
        listOf("ا", "س", "د", "ف", "غ", "ه", "ج", "ك", "ل", "ش"),
        listOf("ز", "خ", "ث", "ط", "ب", "ن", "م", "ذ")
    )

    /**
     * عربي Gboard: نفس ترتيب لوحة جوجل العربية. الصف الثالث فيه 10 حروف (ذ ء ؤ ر ى ة و ز ظ د)
     * فيصير عرض الحذف بعرض حرف عادي ليتساوى عرض الصفوف الثلاثة بدون مفاتيح أضيق من غيرها.
     */
    private val arGboard = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        listOf("ذ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د")
    )

    private val arAlternates: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ء"),
        "و" to listOf("ؤ"),
        "ي" to listOf("ئ", "ى"),
        "ى" to listOf("ي", "ئ"),
        "ه" to listOf("ة"),
        "ل" to listOf("لا", "لأ", "لإ", "لآ"),
        "ت" to listOf("ة"),
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

    val emojiCategories: List<EmojiCategory> get() = EmojiData.categories

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
        ArabicLayout.QWERTY -> arQwerty
        ArabicLayout.GBOARD -> arGboard
    }

    fun rows(state: KeyboardUiState): List<List<KeyDefinition>> = when (state.page) {
        KeyboardPage.LETTERS ->
            if (state.language == KeyboardLanguage.ARABIC) arabicRows(state) else englishRows(state)
        KeyboardPage.SYMBOLS_1 -> symbolRows(state, firstPage = true)
        KeyboardPage.SYMBOLS_2 -> symbolRows(state, firstPage = false)
        KeyboardPage.EMOJI, KeyboardPage.CLIPBOARD, KeyboardPage.EDIT -> emptyList()
    }

    /**
     * الصف الأخير تحت صف المسافة: الكرة الأرضية فقط يسارًا تحت مفتاح 123 بالضبط، والجهة اليمنى فاضية
     * عمدًا لأن زر إخفاء لوحة المفاتيح الخاص بالنظام (شريط تنقل One UI) يظهر هناك، فيتوازن الصف.
     * ضغطة على الكرة = تبديل اللغة (عربي/إنكليزي)، ضغطة مطوّلة = لوحة المفاتيح التالية بالنظام.
     */
    fun utilityRow(): List<KeyDefinition> = listOf(
        KeyDefinition("🌐", KeyAction.SwitchLanguage, weight = 1.25f, longPressAction = KeyAction.Globe, plain = true),
        KeyDefinition.spacer(8.75f)
    )

    private fun numberRow(state: KeyboardUiState): List<List<KeyDefinition>> =
        if (state.numberRow) listOf(digitsFor(state).map { KeyDefinition(it, KeyAction.Character(it)) }) else emptyList()

    /** يوسّط صفًا أقصر من العرض الكامل بفراغات متساوية على الطرفين */
    private fun centered(row: List<KeyDefinition>, gap: Int): List<KeyDefinition> =
        if (gap <= 0) row else listOf(KeyDefinition.spacer(gap / 2f)) + row + listOf(KeyDefinition.spacer(gap / 2f))

    /**
     * أبعاد آيفون المقيسة: مفتاحا Shift والحذف بعرض 1.3 من خانة الحرف، وبينهما وبين الحروف فراغ 0.2 (مقيسة من صورة مرجعية لآيفون)
     * (مجموع كل جانب = 1.5 عندما يكون بالصف 7 حروف).
     */
    private const val EDGE_GAP = 0.2f

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

        // عرض الصف الكامل = 10 وحدات؛ Shift والحذف يأخذان ما تبقى من الصف الثالث (نصف لكل جانب)
        val side = (10 - r3.size) / 2f
        val gap = if (side >= 1.5f) EDGE_GAP else 0f
        val edge = side - gap
        val shiftKey = KeyDefinition(
            label = if (state.shiftState == ShiftState.LOCKED) "⇪" else "⇧",
            action = KeyAction.Shift,
            weight = edge,
            isAccent = state.shiftState != ShiftState.OFF
        )
        val row3 = buildList {
            add(shiftKey)
            if (gap > 0f) add(KeyDefinition.spacer(gap))
            addAll(mapRow(r3))
            if (gap > 0f) add(KeyDefinition.spacer(gap))
            add(KeyDefinition("⌫", KeyAction.Backspace, weight = edge))
        }
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
        // عرض الصف = عدد حروف أطول صف (11 للقياسي والأبجدي، 10 للـ QWERTY).
        // الصف الثالث: فراغ + حروف + فراغ 0.28 + حذف 1.22 بنفس العرض الكامل.
        val width = letters[0].size.toFloat()
        // المساحة المتبقية بعد حروف الصف الثالث تتوزع بين الفراغ والحذف؛ لو ضاقت (10 حروف) يصير الحذف بعرض حرف
        val remaining = width - letters[2].size
        val fits = remaining >= 1.3f + EDGE_GAP
        val backspaceWeight = if (fits) 1.3f else remaining.coerceAtLeast(1f)
        val edgeGap = if (fits) EDGE_GAP else 0f
        val lead = (remaining - backspaceWeight - edgeGap).coerceAtLeast(0f)
        val row3 = buildList {
            if (lead > 0f) add(KeyDefinition.spacer(lead))
            addAll(mapRow(letters[2]))
            if (edgeGap > 0f) add(KeyDefinition.spacer(edgeGap))
            add(KeyDefinition("⌫", KeyAction.Backspace, weight = backspaceWeight))
        }
        val row2 = mapRow(letters[1])
        return numberRow(state) + listOf(
            mapRow(letters[0]),
            if (row2.size < letters[0].size) centered(row2, letters[0].size - row2.size) else row2,
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
        val edge = 1.5f - EDGE_GAP
        val switchKey = if (firstPage)
            KeyDefinition("#+=", KeyAction.SwitchToSymbols2, weight = edge)
        else
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = edge)
        val third = listOf(switchKey, KeyDefinition.spacer(EDGE_GAP)) + mapRow(r3, 1.4f) +
            listOf(KeyDefinition.spacer(EDGE_GAP), KeyDefinition("⌫", KeyAction.Backspace, weight = edge))
        return listOf(mapRow(r1), mapRow(r2), third, bottomRow(state, letters = false), utilityRow())
    }

    /** صف آيفون: [123] [إيموجي] [مسافة] [return] بالنسب المقيسة 1.25 / 1.25 / 5 / 2.5 */
    private fun bottomRow(state: KeyboardUiState, letters: Boolean): List<KeyDefinition> {
        val arabic = state.language == KeyboardLanguage.ARABIC
        val left = if (letters)
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = 1.25f)
        else
            KeyDefinition(if (arabic) "أبج" else "ABC", KeyAction.SwitchToLetters, weight = 1.25f)

        // iOS 18: على شريط المسافة كلمة space / مسافة
        val spaceLabel = if (arabic) "مسافة" else "space"
        val emoji = KeyDefinition("😊", KeyAction.Emoji, weight = 1.25f)

        // مثل آيفون: حقول البريد والروابط تعرض مفاتيح @ . / .com بجانب المسافة
        return when (if (letters) state.fieldKind else FieldKind.TEXT) {
            FieldKind.EMAIL -> listOf(
                left.copy(weight = 1.25f),
                emoji.copy(weight = 1.1f),
                KeyDefinition("@", KeyAction.Character("@"), weight = 1.1f),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 3.3f),
                KeyDefinition(".", KeyAction.Character("."), weight = 1.1f),
                enterKey(state.enterKind, arabic).copy(weight = 2.15f)
            )
            FieldKind.URL -> listOf(
                left.copy(weight = 1.2f),
                emoji.copy(weight = 1.0f),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 2.2f),
                KeyDefinition(".", KeyAction.Character("."), weight = 0.9f),
                KeyDefinition("/", KeyAction.Character("/"), weight = 0.9f),
                KeyDefinition(".com", KeyAction.Character(".com"), weight = 1.5f),
                enterKey(state.enterKind, arabic).copy(weight = 2.3f)
            )
            FieldKind.TEXT -> listOf(
                left,
                emoji,
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 5f),
                enterKey(state.enterKind, arabic)
            )
        }
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
            weight = 2.5f,
            isAccent = kind != EnterKind.RETURN,
            textOnly = true
        )
    }
}
