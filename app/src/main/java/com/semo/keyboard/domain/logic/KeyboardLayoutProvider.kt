package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.FieldKind
import com.semo.keyboard.domain.model.HideButtonMode
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.NumKeyTarget
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

    private val dvorak = listOf(
        listOf("'", ",", ".", "p", "y", "f", "g", "c", "r", "l"),
        listOf("a", "o", "e", "u", "i", "d", "h", "t", "n", "s"),
        listOf(";", "q", "j", "k", "x", "b", "m", "w", "v", "z")
    )
    private val colemak = listOf(
        listOf("q", "w", "f", "p", "g", "j", "l", "u", "y", ";"),
        listOf("a", "r", "s", "t", "d", "h", "n", "e", "i", "o"),
        listOf("z", "x", "c", "v", "b", "k", "m")
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
        "ء" to listOf("َ", "ُ", "ِ", "ّ", "ْ", "ً", "ٌ", "ٍ"),
        "ك" to listOf("گ", "ک"),
        "ب" to listOf("پ"),
        "ج" to listOf("چ"),
        "ز" to listOf("ژ"),
        "ف" to listOf("ڤ")
    )

    // ---------- لغات المكتبة (التخطيط مدمج، والقاموس يُنزَّل من الإعدادات) ----------
    private class LangLayout(val rows: List<List<String>>, val alternates: Map<String, List<String>>)

    private fun r(s: String): List<String> = s.split(' ')

    private val latinQwertyRows = listOf(r("q w e r t y u i o p"), r("a s d f g h j k l"), r("z x c v b n m"))

    private val libraryLayouts: Map<KeyboardLanguage, LangLayout> = mapOf(
        KeyboardLanguage.FRENCH to LangLayout(
            listOf(r("a z e r t y u i o p"), r("q s d f g h j k l m"), r("w x c v b n")),
            mapOf(
                "a" to r("à â æ ä á"), "e" to r("é è ê ë"), "i" to r("î ï í"), "o" to r("ô œ ö ó"),
                "u" to r("ù û ü ú"), "c" to r("ç"), "y" to r("ÿ")
            )
        ),
        KeyboardLanguage.SPANISH to LangLayout(
            listOf(r("q w e r t y u i o p"), r("a s d f g h j k l ñ"), r("z x c v b n m")),
            mapOf("a" to r("á"), "e" to r("é"), "i" to r("í"), "o" to r("ó"), "u" to r("ú ü"), "n" to r("ñ"))
        ),
        KeyboardLanguage.GERMAN to LangLayout(
            listOf(r("q w e r t z u i o p ü"), r("a s d f g h j k l ö ä"), r("y x c v b n m")),
            mapOf("a" to r("ä"), "o" to r("ö"), "u" to r("ü"), "s" to r("ß"), "e" to r("€"))
        ),
        KeyboardLanguage.TURKISH to LangLayout(
            listOf(r("q w e r t y u ı o p ğ ü"), r("a s d f g h j k l ş i"), r("z x c v b n m ö ç")),
            mapOf("a" to r("â"), "u" to r("û"), "i" to r("î"), "o" to r("ô"))
        ),
        KeyboardLanguage.RUSSIAN to LangLayout(
            listOf(r("й ц у к е н г ш щ з х ъ"), r("ф ы в а п р о л д ж э"), r("я ч с м и т ь б ю")),
            mapOf("е" to r("ё"), "ь" to r("ъ"), "ъ" to r("ь"))
        ),
        KeyboardLanguage.UKRAINIAN to LangLayout(
            listOf(r("й ц у к е н г ш щ з х ї"), r("ф і в а п р о л д ж є"), r("я ч с м и т ь б ю ґ")),
            mapOf("и" to r("ы"), "г" to r("ґ"), "ь" to r("ъ"))
        ),
        KeyboardLanguage.PERSIAN to LangLayout(
            listOf(r("ض ص ث ق ف غ ع ه خ ح ج چ"), r("ش س ی ب ل ا ت ن م ک گ"), r("ظ ط ز ر ذ د پ و")),
            mapOf(
                "ا" to r("آ أ إ ء"), "ی" to r("ئ ي ى"), "و" to r("ؤ"), "ه" to r("ة ۀ"), "ک" to r("ك"),
                "ز" to r("ژ"), "ء" to r("َ ُ ِ ّ ْ ً ٌ ٍ")
            )
        ),
        KeyboardLanguage.PORTUGUESE to LangLayout(
            latinQwertyRows,
            mapOf(
                "a" to r("á à â ã ä"), "e" to r("é ê è"), "i" to r("í î"), "o" to r("ó ô õ"),
                "u" to r("ú ü"), "c" to r("ç")
            )
        ),
        KeyboardLanguage.ITALIAN to LangLayout(
            latinQwertyRows,
            mapOf("a" to r("à á"), "e" to r("è é ê"), "i" to r("ì í"), "o" to r("ò ó"), "u" to r("ù ú"))
        ),
        KeyboardLanguage.INDONESIAN to LangLayout(latinQwertyRows, emptyMap()),
        KeyboardLanguage.DUTCH to LangLayout(
            latinQwertyRows,
            mapOf("a" to r("ä á"), "e" to r("é è ë"), "i" to r("ï í"), "o" to r("ö ó"), "u" to r("ü ú"), "c" to r("ç"))
        ),
        KeyboardLanguage.POLISH to LangLayout(
            latinQwertyRows,
            mapOf(
                "a" to r("ą"), "c" to r("ć"), "e" to r("ę"), "l" to r("ł"), "n" to r("ń"),
                "o" to r("ó"), "s" to r("ś"), "z" to r("ź ż")
            )
        )
    )

    private val libraryExtra: Map<KeyboardLanguage, LangLayout> = mapOf(
        KeyboardLanguage.CZECH to LangLayout(
            listOf(r("q w e r t z u i o p ú"), r("a s d f g h j k l ů"), r("y x c v b n m")),
            mapOf(
                "a" to r("á ä"), "c" to r("č ć"), "d" to r("ď"), "e" to r("é ě ë"), "i" to r("í"), "n" to r("ň"),
                "o" to r("ó ö"), "r" to r("ř"), "s" to r("š"), "t" to r("ť"), "u" to r("ů ü"), "y" to r("ý"), "z" to r("ž")
            )
        ),
        KeyboardLanguage.ROMANIAN to LangLayout(
            latinQwertyRows,
            mapOf("a" to r("ă â"), "i" to r("î"), "s" to r("ș"), "t" to r("ț"))
        ),
        KeyboardLanguage.SWEDISH to LangLayout(
            listOf(r("q w e r t y u i o p å"), r("a s d f g h j k l ö ä"), r("z x c v b n m")),
            mapOf("e" to r("é"), "a" to r("á à"), "u" to r("ü"), "c" to r("ç"))
        ),
        KeyboardLanguage.DANISH to LangLayout(
            listOf(r("q w e r t y u i o p å"), r("a s d f g h j k l æ ø"), r("z x c v b n m")),
            mapOf("e" to r("é ë"), "a" to r("á ä"), "o" to r("ö"), "u" to r("ü"))
        ),
        KeyboardLanguage.NORWEGIAN to LangLayout(
            listOf(r("q w e r t y u i o p å"), r("a s d f g h j k l ø æ"), r("z x c v b n m")),
            mapOf("e" to r("é è ê"), "a" to r("á à ä"), "o" to r("ó ò ö"), "u" to r("ü"))
        ),
        KeyboardLanguage.FINNISH to LangLayout(
            listOf(r("q w e r t y u i o p å"), r("a s d f g h j k l ö ä"), r("z x c v b n m")),
            mapOf("s" to r("š"), "z" to r("ž"))
        ),
        KeyboardLanguage.HUNGARIAN to LangLayout(
            listOf(r("q w e r t z u i o p ő ú"), r("a s d f g h j k l é á ű"), r("y x c v b n m ö ü")),
            mapOf("a" to r("á"), "e" to r("é"), "i" to r("í"), "o" to r("ó ö ő"), "u" to r("ú ü ű"))
        ),
        KeyboardLanguage.CATALAN to LangLayout(
            latinQwertyRows,
            mapOf(
                "a" to r("à á"), "e" to r("è é"), "i" to r("í ï"), "o" to r("ò ó"), "u" to r("ú ü"),
                "c" to r("ç"), "l" to r("l·"), "n" to r("ñ")
            )
        )
    )

    /** An alternative letter arrangement for a language: [id] is stored in settings, [label] is shown as is. */
    data class LayoutVariant(val id: String, val label: String, val rows: List<List<String>>)


    private fun allLibrary(): Map<KeyboardLanguage, LangLayout> = libraryLayouts + libraryExtra

    private val variantTable: Map<KeyboardLanguage, List<LayoutVariant>> by lazy {
        mapOf(
            KeyboardLanguage.FRENCH to listOf(
                LayoutVariant("default", "AZERTY", allLibrary().getValue(KeyboardLanguage.FRENCH).rows),
                LayoutVariant("qwerty", "QWERTY", latinQwertyRows)
            ),
            KeyboardLanguage.GERMAN to listOf(
                LayoutVariant("default", "QWERTZ", allLibrary().getValue(KeyboardLanguage.GERMAN).rows),
                LayoutVariant("qwerty", "QWERTY", listOf(r("q w e r t y u i o p ü"), r("a s d f g h j k l ö ä"), r("z x c v b n m")))
            ),
            KeyboardLanguage.CZECH to listOf(
                LayoutVariant("default", "QWERTZ", allLibrary().getValue(KeyboardLanguage.CZECH).rows),
                LayoutVariant("qwerty", "QWERTY", listOf(r("q w e r t y u i o p ú"), r("a s d f g h j k l ů"), r("z x c v b n m")))
            ),
            KeyboardLanguage.HUNGARIAN to listOf(
                LayoutVariant("default", "QWERTZ", allLibrary().getValue(KeyboardLanguage.HUNGARIAN).rows),
                LayoutVariant("qwerty", "QWERTY", listOf(r("q w e r t y u i o p ő ú"), r("a s d f g h j k l é á ű"), r("z x c v b n m ö ü")))
            ),
            KeyboardLanguage.TURKISH to listOf(
                LayoutVariant("default", "Q", allLibrary().getValue(KeyboardLanguage.TURKISH).rows),
                LayoutVariant("f", "F", listOf(r("f g ğ ı o d r n h p q w"), r("u i e a ü t k m l y ş"), r("j ö v c ç z s b x")))
            )
        )
    }

    fun variantsFor(language: KeyboardLanguage): List<LayoutVariant> = variantTable[language].orEmpty()

    private fun variantFor(language: KeyboardLanguage, variants: Map<String, String>): LayoutVariant? {
        val list = variantTable[language] ?: return null
        return list.firstOrNull { it.id == variants[language.code] } ?: list.first()
    }

    private val persianDigitsList = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰")

    // ---------- رموز ----------
    private val latinDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val arabicDigitsList = listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠")
    private val sym1Row2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
    private val sym1Row3En = listOf(".", ",", "?", "!", "'")
    private val sym1Row3Ar = listOf(".", "،", "؟", "!", "'")
    private val sym2Row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val sym2Row2 = listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")

    /** Curated categories plus every other emoji of the Unicode set ([EmojiExtra]); built once. */
    val emojiCategories: List<EmojiCategory> by lazy {
        EmojiData.categories.map { cat ->
            cat.copy(emojis = (cat.emojis + EmojiExtra.forCategory(cat.titleEn)).distinct())
        }
    }

    /** الأرقام المعروضة حسب لغة اللوحة وخيار الأرقام الهندية */
    private fun digitsFor(state: KeyboardUiState): List<String> = when {
        !state.arabicDigits -> latinDigits
        state.language == KeyboardLanguage.ARABIC -> arabicDigitsList
        state.language == KeyboardLanguage.PERSIAN -> persianDigitsList
        else -> latinDigits
    }

    /** حروف الصفوف الثلاثة لمعاينة الترتيب في الإعدادات */
    fun previewRows(
        language: KeyboardLanguage,
        english: EnglishLayout,
        arabic: ArabicLayout,
        variants: Map<String, String> = emptyMap()
    ): List<List<String>> =
        when (language) {
            KeyboardLanguage.ENGLISH -> englishLetterRows(english)
            KeyboardLanguage.ARABIC -> arabicLetterRows(arabic)
            else -> variantFor(language, variants)?.rows ?: allLibrary()[language]?.rows ?: englishLetterRows(english)
        }

    private fun englishLetterRows(layout: EnglishLayout): List<List<String>> = when (layout) {
        EnglishLayout.QWERTY -> qwerty
        EnglishLayout.AZERTY -> azerty
        EnglishLayout.QWERTZ -> qwertz
        EnglishLayout.DVORAK -> dvorak
        EnglishLayout.COLEMAK -> colemak
    }

    private fun arabicLetterRows(layout: ArabicLayout): List<List<String>> = when (layout) {
        ArabicLayout.STANDARD -> arStandard
        ArabicLayout.ALPHABETIC -> arAlphabetic
        ArabicLayout.QWERTY -> arQwerty
        ArabicLayout.GBOARD -> arGboard
    }

    fun rows(state: KeyboardUiState): List<List<KeyDefinition>> = when (state.page) {
        KeyboardPage.LETTERS -> when (state.language) {
            KeyboardLanguage.ENGLISH -> casedRows(state, englishLetterRows(state.englishLayout), enAlternates)
            KeyboardLanguage.ARABIC -> uncasedRows(state, arabicLetterRows(state.arabicLayout), arAlternates)
            else -> {
                val layout = allLibrary()[state.language]
                val letters = variantFor(state.language, state.layoutVariants)?.rows ?: layout?.rows
                when {
                    layout == null || letters == null -> casedRows(state, englishLetterRows(state.englishLayout), enAlternates)
                    state.language.cased -> casedRows(state, letters, layout.alternates)
                    else -> uncasedRows(state, letters, layout.alternates)
                }
            }
        }
        KeyboardPage.SYMBOLS_1 -> symbolRows(state, firstPage = true)
        KeyboardPage.SYMBOLS_2 -> symbolRows(state, firstPage = false)
        KeyboardPage.NUMPAD -> numpadRows(state)
        KeyboardPage.EMOJI, KeyboardPage.CLIPBOARD, KeyboardPage.EDIT, KeyboardPage.CALC, KeyboardPage.TRANSLATE -> emptyList()
    }

    /**
     * الصف الأخير تحت صف المسافة: دائرتان متناظرتان بمحاذاة أزرار شريط التنقل.
     * يسارًا الكرة الأرضية (ضغطة = تبديل عربي/إنكليزي، ضغطة مطوّلة = لوحة النظام التالية) وعليها شارة
     * بلغة الكتابة الحالية، ويمينًا دائرة إخفاء اللوحة: تُرسم خلف سهم الإخفاء الخاص بالنظام (One UI)
     * فيبدو داخل دائرة تنسجم مع دائرة الكرة، أو بسهم خاص بالتطبيق حسب الإعدادات.
     */
    private fun utilityTail(state: KeyboardUiState): List<List<KeyDefinition>> =
        if (state.navButtons) emptyList() else listOf(utilityRow(state))

    fun utilityRow(state: KeyboardUiState): List<KeyDefinition> {
        val globe = KeyDefinition(
            "🌐", KeyAction.SwitchLanguage, weight = 2.8f,
            longPressAction = KeyAction.Globe, plain = true,
            // مع أكثر من لغتين نعرض رمز اللغة الحالية على الكرة كي يعرف المستخدم أين وصل بالدورة
            badge = if (state.enabledLanguages.size > 2) state.language.badge else ""
        )
        val hide = when (state.hideButtonMode) {
            HideButtonMode.OFF -> KeyDefinition.spacer(2.8f)
            HideButtonMode.RING -> KeyDefinition("⌄", KeyAction.HideKeyboard, weight = 2.8f, plain = true, ringOnly = true)
            HideButtonMode.ARROW -> KeyDefinition("⌄", KeyAction.HideKeyboard, weight = 2.8f, plain = true)
        }
        return listOf(globe, KeyDefinition.spacer(4.4f), hide)
    }

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

    /** الحرف بعد Shift: التركية تحتاج İ/I، والألمانية تُبقي ß كما هي */
    private fun upperOf(language: KeyboardLanguage, s: String): String = when {
        language == KeyboardLanguage.TURKISH && s == "i" -> "İ"
        language == KeyboardLanguage.TURKISH && s == "ı" -> "I"
        s == "ß" -> s
        else -> s.uppercase()
    }

    /** لغات بحروف كبيرة وصغيرة (لاتيني وسيريلي): صف ثالث بـ Shift يسارًا وحذف يمينًا */
    private fun casedRows(
        state: KeyboardUiState,
        letters: List<List<String>>,
        alternates: Map<String, List<String>>
    ): List<List<KeyDefinition>> {
        val upper = state.shiftState != ShiftState.OFF
        val language = state.language
        fun mapRow(chars: List<String>) = chars.map { base ->
            val shown = if (upper) upperOf(language, base) else base
            val alts = alternates[base].orEmpty().map { if (upper) upperOf(language, it) else it }
            KeyDefinition(shown, KeyAction.Character(shown), longPressChars = alts)
        }
        val r1 = letters[0]
        val r2 = letters[1]
        val r3 = letters[2]
        // عرض الصف الكامل = عدد حروف أطول صف (10 للاتيني القياسي، 11 أو 12 لبعض اللغات)
        val width = r1.size
        val side = (width - r3.size) / 2f
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
            centered(mapRow(r2), width - r2.size),
            row3,
            bottomRow(state, letters = true)
        ) + utilityTail(state)
    }

    /** لغات بلا حروف كبيرة (عربي وفارسي): صف ثالث بحذف يمينًا بلا Shift */
    private fun uncasedRows(
        state: KeyboardUiState,
        letters: List<List<String>>,
        alternates: Map<String, List<String>>
    ): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>) = chars.map { c ->
            KeyDefinition(c, KeyAction.Character(c), longPressChars = alternates[c].orEmpty())
        }
        // عرض الصف = عدد حروف أطول صف (11 للقياسي والأبجدي، 10 للـ QWERTY، 12 للفارسي).
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
        ) + utilityTail(state)
    }

    /**
     * لوحة الأرقام: شبكة 3×4 للأرقام (1-9 ثم , 0 .) بالوسط، وعلى جانبيها عمودا رموز العمليات والرموز الشائعة
     * (+ − × ÷ ( ) /) والحذف والمسافة والإدخال، بنفس عدد صفوف صفحة الحروف فلا يقفز ارتفاع اللوحة عند التبديل.
     * بقية الرموز (= % * # [ ] { } _ ± ...) بالضغط المطوّل على المفاتيح المجاورة، وصفحة الرموز كاملة بمفتاح «رموز».
     * المفاتيح ستة أعمدة متساوية العرض.
     */
    private fun numpadRows(state: KeyboardUiState): List<List<KeyDefinition>> {
        val arabic = state.language.rtl
        val spaceWord = state.language.spaceLabel
        val d = digitsFor(state)
        fun digit(i: Int, alts: List<String> = emptyList()) =
            KeyDefinition(d[i], KeyAction.Character(d[i]), longPressChars = alts)
        fun op(label: String, char: String = label, alts: List<String> = emptyList()) =
            KeyDefinition(label, KeyAction.Character(char), longPressChars = alts, special = true)
        fun plain(char: String, alts: List<String> = emptyList()) =
            KeyDefinition(char, KeyAction.Character(char), longPressChars = alts)

        val r1 = listOf(
            op("+", alts = listOf("=", "%", "±", "#")),
            op("(", alts = listOf("[", "{", "<")),
            digit(0), digit(1), digit(2),
            KeyDefinition(spaceWord, KeyAction.Space)
        )
        val r2 = listOf(
            // الناقص يُعرض بعلامة − لكنه يُكتب - عادية كي تفهمه حقول الأرقام
            op("−", "-", alts = listOf("_", "–", "—")),
            op(")", alts = listOf("]", "}", ">")),
            digit(3), digit(4), digit(5),
            enterKey(state.enterKind, state.language).copy(weight = 1f)
        )
        val r3 = listOf(
            op("×", alts = listOf("*", "⋅", "x")),
            op("/", alts = listOf("\\", "|", ":")),
            digit(6), digit(7), digit(8),
            KeyDefinition("⌫", KeyAction.Backspace)
        )
        val r4 = listOf(
            KeyDefinition(if (arabic) "أبج" else "ABC", KeyAction.SwitchToLetters),
            op("÷", alts = listOf(":", "‰", "√")),
            plain(",", alts = listOf(";", ":")),
            digit(9, alts = listOf("%", "°", "‰")),
            plain(".", alts = listOf("…", "٫", "·")),
            KeyDefinition(if (arabic) "رموز" else "#+=", KeyAction.SwitchToSymbols, textOnly = true)
        )
        return listOf(r1, r2, r3, r4) + utilityTail(state)
    }

    private fun symbolRows(state: KeyboardUiState, firstPage: Boolean): List<List<KeyDefinition>> {
        fun mapRow(chars: List<String>, weight: Float = 1f) =
            chars.map { KeyDefinition(it, KeyAction.Character(it), weight = weight) }

        val r1 = if (firstPage) digitsFor(state) else sym2Row1
        val r2 = if (firstPage) sym1Row2 else sym2Row2
        val r3 = if (state.language.rtl) sym1Row3Ar else sym1Row3En
        val edge = 1.5f - EDGE_GAP
        val switchKey = if (firstPage)
            KeyDefinition("#+=", KeyAction.SwitchToSymbols2, weight = edge)
        else
            KeyDefinition("123", KeyAction.SwitchToSymbols, weight = edge)
        val third = listOf(switchKey, KeyDefinition.spacer(EDGE_GAP)) + mapRow(r3, 1.4f) +
            listOf(KeyDefinition.spacer(EDGE_GAP), KeyDefinition("⌫", KeyAction.Backspace, weight = edge))
        return listOf(mapRow(r1), mapRow(r2), third, bottomRow(state, letters = false)) + utilityTail(state)
    }

    /** صف آيفون: [123] [إيموجي] [مسافة] [return] بالنسب المقيسة 1.25 / 1.25 / 5 / 2.5 */
    private fun bottomRow(state: KeyboardUiState, letters: Boolean): List<KeyDefinition> {
        val arabic = state.language.rtl
        // «123»: ضغطة = الوجهة المختارة بالإعدادات (لوحة الأرقام أو الرموز)، ومطوّلة = الوجهة الأخرى
        val left = if (letters)
            KeyDefinition(
                "123", KeyAction.SwitchToNumbers, weight = 1.25f,
                longPressAction = if (state.numKeyTarget == NumKeyTarget.NUMPAD) KeyAction.SwitchToSymbols else KeyAction.SwitchToNumpad
            )
        else
            KeyDefinition(if (arabic) "أبج" else "ABC", KeyAction.SwitchToLetters, weight = 1.25f)

        // iOS 18: على شريط المسافة كلمة space / مسافة
        val spaceLabel = state.language.spaceLabel
        val emoji = KeyDefinition("😊", KeyAction.Emoji, weight = 1.25f)
        val nav = state.navButtons
        val globe = KeyDefinition("🌐", KeyAction.SwitchLanguage, weight = 1f, longPressAction = KeyAction.Globe)

        // مثل آيفون: حقول البريد والروابط تعرض مفاتيح @ . / .com بجانب المسافة
        return when (if (letters) state.fieldKind else FieldKind.TEXT) {
            FieldKind.EMAIL -> if (nav) listOf(
                left.copy(weight = 1.25f),
                globe.copy(weight = 1.0f),
                emoji.copy(weight = 1.0f),
                KeyDefinition("@", KeyAction.Character("@"), weight = 1.0f, longPressChars = listOf("_", "-", "+")),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 2.6f),
                KeyDefinition(".", KeyAction.Character("."), weight = 1.0f),
                enterKey(state.enterKind, state.language).copy(weight = 2.15f)
            ) else listOf(
                left.copy(weight = 1.25f),
                emoji.copy(weight = 1.1f),
                KeyDefinition("@", KeyAction.Character("@"), weight = 1.1f, longPressChars = listOf("_", "-", "+")),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 3.3f),
                KeyDefinition(".", KeyAction.Character("."), weight = 1.1f),
                enterKey(state.enterKind, state.language).copy(weight = 2.15f)
            )
            FieldKind.URL -> if (nav) listOf(
                left.copy(weight = 1.2f),
                globe.copy(weight = 0.9f),
                emoji.copy(weight = 0.9f),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 2.4f),
                KeyDefinition(".", KeyAction.Character("."), weight = 0.8f, longPressChars = urlDotAlts),
                KeyDefinition("/", KeyAction.Character("/"), weight = 0.8f, longPressChars = urlSlashAlts),
                KeyDefinition(".com", KeyAction.Character(".com"), weight = 1.2f, longPressChars = urlDomainAlts),
                enterKey(state.enterKind, state.language).copy(weight = 2.0f)
            ) else listOf(
                left.copy(weight = 1.2f),
                emoji.copy(weight = 1.0f),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 3.0f),
                KeyDefinition(".", KeyAction.Character("."), weight = 0.8f, longPressChars = urlDotAlts),
                KeyDefinition("/", KeyAction.Character("/"), weight = 0.8f, longPressChars = urlSlashAlts),
                KeyDefinition(".com", KeyAction.Character(".com"), weight = 1.3f, longPressChars = urlDomainAlts),
                enterKey(state.enterKind, state.language).copy(weight = 2.0f)
            )
            FieldKind.TEXT -> if (nav) listOf(
                left,
                globe,
                emoji.copy(weight = 1.0f),
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 4.25f),
                enterKey(state.enterKind, state.language)
            ) else listOf(
                left,
                emoji,
                KeyDefinition(spaceLabel, KeyAction.Space, weight = 5f),
                enterKey(state.enterKind, state.language)
            )
        }
    }

    private val urlDotAlts = listOf("-", "_", "~")
    private val urlSlashAlts = listOf("?", "&", "=", "#", ":")
    private val urlDomainAlts = listOf(".net", ".org", ".sa", ".edu", ".io")

    private fun enterKey(kind: EnterKind, language: KeyboardLanguage): KeyDefinition {
        val arabic = language.rtl
        val text = when (kind) {
            EnterKind.RETURN -> language.returnLabel
            EnterKind.SEARCH -> if (arabic) "بحث" else "Search"
            EnterKind.SEND -> if (arabic) "إرسال" else "Send"
            EnterKind.GO -> if (arabic) "اذهب" else "Go"
            EnterKind.DONE -> if (arabic) "تم" else "Done"
            EnterKind.NEXT -> if (arabic) "التالي" else "Next"
        }
        // مفتاح الإدخال أزرق دائمًا بكل الصفحات (return/بحث/إرسال/اذهب...)
        return KeyDefinition(
            label = text,
            action = KeyAction.Enter,
            weight = 2.5f,
            isAccent = true,
            textOnly = true
        )
    }
}
