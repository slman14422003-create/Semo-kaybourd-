package com.semo.keyboard.domain.model

import androidx.compose.runtime.Immutable

/** أنواع المفاتيح المدعومة داخل اللوحة */
sealed class KeyAction {
    data class Character(val char: String) : KeyAction()
    data object Shift : KeyAction()
    data object Backspace : KeyAction()
    data object Space : KeyAction()
    data object Enter : KeyAction()
    data object SwitchToSymbols : KeyAction()
    data object SwitchToSymbols2 : KeyAction()
    data object SwitchToLetters : KeyAction()
    /** مفتاح «123» بصفحة الحروف: يفتح لوحة الأرقام أو صفحة الرموز حسب الإعداد */
    data object SwitchToNumbers : KeyAction()
    /** يفتح لوحة الأرقام 3×4 مباشرة (ضغطة مطوّلة على «123» أو من شريط الأدوات) */
    data object SwitchToNumpad : KeyAction()
    data object SwitchLanguage : KeyAction()
    data object Emoji : KeyAction()
    data object Globe : KeyAction()
    /** الإدخال الصوتي (الميكروفون بالصف السفلي) */
    data object Mic : KeyAction()
    /** إخفاء لوحة المفاتيح (الدائرة اليمنى بالصف السفلي، بمحاذاة زر الإخفاء الخاص بالنظام) */
    data object HideKeyboard : KeyAction()
    data object None : KeyAction()
}

/** تعريف مفتاح واحد على اللوحة، مع وزن العرض النسبي */
@Immutable
data class KeyDefinition(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val isAccent: Boolean = false,
    /** بدائل تظهر في شريط الاقتراحات عند الضغط المطوّل (مثل a → à á â) */
    val longPressChars: List<String> = emptyList(),
    /** true = يُعرض النص فقط حتى لو للمفتاح أيقونة (مثل مفتاح "بحث") */
    val textOnly: Boolean = false,
    /** فراغ غير قابل للضغط لتوسيط الصفوف الأقصر */
    val isSpacer: Boolean = false,
    /** إجراء بديل عند الضغط المطوّل (مثلًا الكرة الأرضية: ضغطة = لغة، مطوّلة = لوحة النظام التالية) */
    val longPressAction: KeyAction? = null,
    /** مفتاح بلا خلفية مربعة: يُرسم داخل دائرة (الكرة الأرضية وزر الإخفاء بالصف السفلي) */
    val plain: Boolean = false,
    /** شارة صغيرة فوق الدائرة (رمز اللغة الحالية EN / ع) */
    val badge: String = "",
    /** دائرة بلا أيقونة: تُرسم خلف سهم الإخفاء الخاص بالنظام فيبدو داخل الدائرة */
    val ringOnly: Boolean = false,
    /** يُرسم بلون المفاتيح الوظيفية (الرمادي) حتى لو كان حرفًا عاديًا، مثل رموز العمليات بلوحة الأرقام */
    val special: Boolean = false
) {
    companion object {
        fun spacer(weight: Float) = KeyDefinition("", KeyAction.None, weight, isSpacer = true)
    }
}

enum class ShiftState { OFF, ON, LOCKED }
enum class KeyboardPage { LETTERS, SYMBOLS_1, SYMBOLS_2, NUMPAD, EMOJI, CLIPBOARD, EDIT, CALC, TRANSLATE }
/** نظام الكتابة: يحدّد الحروف الكبيرة (Shift) واتجاه النص */
enum class Script { LATIN, ARABIC, CYRILLIC }

/**
 * لغات اللوحة. العربي والإنكليزي مدمجان (قاموسهما الأساسي بالتطبيق)، وباقي اللغات تُنزَّل من «مكتبة اللغات»
 * بالإعدادات (قاموس شيوع + نموذج جمل) مثل باقي لوحات المفاتيح. تخطيط الأحرف نفسه مدمج صغير لكل لغة.
 *
 * [code] رمز قوائم FrequencyWords، [tatoeba] رمز ملفات جمل Tatoeba، [badge] شارة الكرة الأرضية.
 */
enum class KeyboardLanguage(
    val code: String,
    val tatoeba: String,
    val nativeName: String,
    val badge: String,
    val script: Script,
    val spaceLabel: String,
    val returnLabel: String
) {
    ENGLISH("en", "eng", "English", "EN", Script.LATIN, "space", "return"),
    ARABIC("ar", "ara", "العربية", "ع", Script.ARABIC, "مسافة", "إدخال"),
    FRENCH("fr", "fra", "Français", "FR", Script.LATIN, "espace", "retour"),
    SPANISH("es", "spa", "Español", "ES", Script.LATIN, "espacio", "intro"),
    GERMAN("de", "deu", "Deutsch", "DE", Script.LATIN, "Leerzeichen", "Return"),
    TURKISH("tr", "tur", "Türkçe", "TR", Script.LATIN, "boşluk", "return"),
    RUSSIAN("ru", "rus", "Русский", "RU", Script.CYRILLIC, "пробел", "ввод"),
    PERSIAN("fa", "pes", "فارسی", "فا", Script.ARABIC, "فاصله", "ورود"),
    PORTUGUESE("pt", "por", "Português", "PT", Script.LATIN, "espaço", "retorno"),
    ITALIAN("it", "ita", "Italiano", "IT", Script.LATIN, "spazio", "invio"),
    INDONESIAN("id", "ind", "Indonesia", "ID", Script.LATIN, "spasi", "return"),
    DUTCH("nl", "nld", "Nederlands", "NL", Script.LATIN, "spatie", "return"),
    POLISH("pl", "pol", "Polski", "PL", Script.LATIN, "spacja", "enter"),
    UKRAINIAN("uk", "ukr", "Українська", "UK", Script.CYRILLIC, "пробіл", "ввід"),
    CZECH("cs", "ces", "Čeština", "CS", Script.LATIN, "mezera", "enter"),
    ROMANIAN("ro", "ron", "Română", "RO", Script.LATIN, "spațiu", "enter"),
    SWEDISH("sv", "swe", "Svenska", "SV", Script.LATIN, "mellanslag", "retur"),
    DANISH("da", "dan", "Dansk", "DA", Script.LATIN, "mellemrum", "retur"),
    NORWEGIAN("no", "nob", "Norsk", "NO", Script.LATIN, "mellomrom", "retur"),
    FINNISH("fi", "fin", "Suomi", "FI", Script.LATIN, "välilyönti", "enter"),
    HUNGARIAN("hu", "hun", "Magyar", "HU", Script.LATIN, "szóköz", "enter"),
    CATALAN("ca", "cat", "Català", "CA", Script.LATIN, "espai", "retorn");

    /** اللغة مدمجة بالتطبيق (العربي والإنكليزي) فلا تحتاج تنزيلًا لتعمل */
    val builtIn: Boolean get() = this == ENGLISH || this == ARABIC

    /** اتجاه الكتابة من اليمين لليسار (واجهة اللوحة تعرض نصوصًا عربية معها) */
    val rtl: Boolean get() = script == Script.ARABIC

    /** للغة حروف كبيرة وصغيرة فيعمل معها Shift */
    val cased: Boolean get() = script != Script.ARABIC
}
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** ترتيبات المفاتيح الإنكليزية */
enum class EnglishLayout { QWERTY, AZERTY, QWERTZ, DVORAK, COLEMAK }

/** ترتيبات المفاتيح العربية */
enum class ArabicLayout { STANDARD, ALPHABETIC, QWERTY, GBOARD }

/** نوع حقل الإدخال: يغيّر الصف السفلي (بريد: @ و . ، رابط: / و .com) */
enum class FieldKind { TEXT, EMAIL, URL }

/** شكل اللوحة: iOS 18 فقط (تمت إزالة نمط iOS 26) */
enum class KeyboardStyle { IOS18 }

enum class AppLanguage { SYSTEM, ARABIC, ENGLISH }

enum class KeyboardTheme { IOS, MIDNIGHT, OCEAN, FOREST, ROSE, SUNSET, LAVENDER, GRAPHITE, MINT }

/** ارتفاع المفاتيح */
enum class KeyboardSize { SMALL, MEDIUM, LARGE }

/** المسافة بين المفاتيح (أفقيًا وعموديًا): ضيّق، عادي، واسع */
enum class KeySpacing(val horizontal: Float, val vertical: Float) {
    TIGHT(0.7f, 0.8f),
    NORMAL(1f, 1f),
    WIDE(1.35f, 1.15f)
}

/** عناصر شريط الأدوات: يختار المستخدم أيها يظهر وبأي ترتيب */
enum class ToolbarItem { EMOJI, CLIPBOARD, NUMPAD, CALC, CURSOR, ONEHAND, SETTINGS, TRANSLATE, VOICE }

/** ماذا يفتح مفتاح «123» بصفحة الحروف: لوحة أرقام 3×4 أو صفحة الرموز القديمة */
enum class NumKeyTarget { NUMPAD, SYMBOLS }

/** وضع اليد الواحدة: اللوحة مزاحة لليسار أو لليمين */
enum class OneHandMode { OFF, LEFT, RIGHT }

/**
 * دائرة إخفاء اللوحة بالصف السفلي:
 * ARROW = دائرة مع سهم خاص بالتطبيق (الافتراضي، لأن الدائرتين مرفوعتان عن شريط التنقل)،
 * RING = دائرة فقط خلف سهم النظام (تصلح عندما تكون الدائرة ملاصقة للشريط، رفع = 0)،
 * OFF = بلا دائرة.
 */
enum class HideButtonMode { RING, ARROW, OFF }

/** مهلة الضغط المطوّل (بدائل الحروف، لوحة اللمس): قصيرة 0.2 ثانية، متوسطة 0.3، طويلة 0.5 */
enum class HoldDelay(val millis: Long) { SHORT(200L), MEDIUM(300L), LONG(500L) }

/** سرعة الحذف المتكرر عند الاستمرار بالضغط على Backspace (بالميلي ثانية) */
enum class BackspaceSpeed(val firstDelay: Long, val slowRepeat: Long, val fastRepeat: Long) {
    SLOW(500L, 85L, 55L),
    NORMAL(400L, 55L, 32L),
    FAST(300L, 38L, 22L)
}

/** الضغطة المطوّلة على شريط المسافة: تحريك المؤشر، تبديل اللغة، أو لا شيء */
enum class SpaceBarAction { CURSOR, SWITCH_LANGUAGE, NONE }

/**
 * سرعة كل أنيميشن اللوحة (تبديل الصفحات، اللغة، ضغط المفاتيح...): مضاعِف واحد يُطبَّق على كل
 * مدد الحركة بالتطبيق. OFF يلغي الحركة تمامًا (فورية 0ms) لأضعف الأجهزة أو من لا يريدها إطلاقًا.
 */
enum class AnimationSpeed(val multiplier: Float, val label: String) {
    OFF(0f, "إيقاف"),
    FAST(0.55f, "سريعة"),
    NORMAL(1f, "عادية"),
    SLOW(1.7f, "بطيئة")
}

/** اختصار نصي: كتابة [abbr] ثم مسافة تحوّله إلى [expansion] */
data class TextShortcut(val abbr: String, val expansion: String)

/** أدوات التحرير في صفحة التحرير */
enum class EditAction { SELECT_ALL, CUT, COPY, PASTE, LEFT, RIGHT, UP, DOWN, HOME, END, UNDO, REDO }

/** شكل زر الإدخال حسب نوع الحقل (إدخال عادي، بحث، إرسال ...) */
enum class EnterKind { RETURN, SEARCH, SEND, GO, DONE, NEXT }

/** عنصر من سجل الحافظة */
@Immutable
data class ClipItem(val text: String, val pinned: Boolean)

/** تعديل عنصر حافظة جارٍ: [key] المفتاح المخزَّن الأصلي، [text] النص قيد التعديل (يُكتب بلوحة المفاتيح نفسها) */
@Immutable
data class ClipEdit(val key: String, val text: String)

/**
 * عنصر الحافظة الصورة يُخزَّن كنص خاص: بادئة + اسم الملف + نوع الصورة. الملف نفسه بتخزين التطبيق
 * (ClipImageStore)، وبهذا لا يتغيّر شكل تخزين الحافظة ولا حدّ العناصر ولا التثبيت.
 */
const val CLIP_IMAGE_PREFIX = "\u0002img|"

fun clipImageKey(fileName: String, mime: String): String = "$CLIP_IMAGE_PREFIX$fileName|$mime"
fun String.isClipImage(): Boolean = startsWith(CLIP_IMAGE_PREFIX)
fun String.clipImageName(): String = removePrefix(CLIP_IMAGE_PREFIX).substringBefore('|')
fun String.clipImageMime(): String = removePrefix(CLIP_IMAGE_PREFIX).substringAfter('|', "image/png")
val ClipItem.isImage: Boolean get() = text.isClipImage()

/**
 * عنصر الحافظة النص الطويل (أكواد، مقالات...): النص الكامل بملف بتخزين التطبيق (ClipTextStore) كي لا يتضخّم
 * مخزن التفضيلات ولا يبطؤ، ويُخزَّن بالسجل مفتاح خاص: بادئة + اسم الملف + عدد الأحرف + معاينة أول النص.
 */
const val CLIP_LONG_PREFIX = "\u0003txt|"

fun clipLongKey(fileName: String, length: Int, preview: String): String = "$CLIP_LONG_PREFIX$fileName|$length|$preview"
fun String.isClipLong(): Boolean = startsWith(CLIP_LONG_PREFIX)
fun String.clipLongName(): String = removePrefix(CLIP_LONG_PREFIX).substringBefore('|')
fun String.clipLongLength(): Int = removePrefix(CLIP_LONG_PREFIX).substringAfter('|', "").substringBefore('|').toIntOrNull() ?: 0
fun String.clipLongPreview(): String = removePrefix(CLIP_LONG_PREFIX).substringAfter('|', "").substringAfter('|', "")
val ClipItem.isLongText: Boolean get() = text.isClipLong()

/** النص الذي يُعرض للمستخدم: المعاينة للنص الطويل، والنص نفسه لغيره */
fun String.clipDisplayText(): String = if (isClipLong()) clipLongPreview() else this

/** ناتج عملية حسابية مكتوبة قبل المؤشر (مثل 60*30+20 → 1820) */
@Immutable
data class MathResult(val expression: String, val value: String)

/** تفضيلات المستخدم المحفوظة */
data class SemoSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    /** مستوى صوت الضغط 0..1 */
    val soundVolume: Float = 0.5f,
    val hapticEnabled: Boolean = true,
    val numberRow: Boolean = false,
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val onboardingCompleted: Boolean = false,
    val englishLayout: EnglishLayout = EnglishLayout.QWERTY,
    val arabicLayout: ArabicLayout = ArabicLayout.STANDARD,
    val style: KeyboardStyle = KeyboardStyle.IOS18,
    val size: KeyboardSize = KeyboardSize.MEDIUM,
    /** المسافة بين المفاتيح */
    val keySpacing: KeySpacing = KeySpacing.NORMAL,
    /** ماذا يفتح مفتاح «123» */
    val numKeyTarget: NumKeyTarget = NumKeyTarget.SYMBOLS,
    /** عناصر شريط الأدوات الظاهرة بالترتيب */
    val toolbarItems: List<ToolbarItem> = ToolbarItem.values().toList(),
    val oneHand: OneHandMode = OneHandMode.OFF,
    val suggestionsEnabled: Boolean = true,
    val keyPreview: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val arabicDigits: Boolean = false,
    /** الكتابة بالسحب على الحروف (QuickPath) */
    val swipeTyping: Boolean = true,
    /** تصحيح الاختصارات والأخطاء الشائعة (dont → don't) */
    val autoCorrect: Boolean = true,
    val autoSentenceFix: Boolean = true,
    val layoutVariants: Map<String, String> = emptyMap(),
    val emojiFavorites: List<String> = emptyList(),
    val emojiTones: Map<String, String> = emptyMap(),
    /** إظهار ناتج العمليات الحسابية بشريط الاقتراحات */
    val mathResults: Boolean = true,
    /** تعديل يدوي لارتفاع أيقونة الكرة الأرضية (dp، موجب = لفوق) لمحاذاتها تمامًا مع أزرار شريط التنقل */
    val globeOffsetDp: Float = 0f,
    /** شكل دائرة إخفاء اللوحة بالصف السفلي */
    val hideButtonMode: HideButtonMode = HideButtonMode.ARROW,
    /** رفع دائرتي الصف السفلي عن أسفل الشاشة (dp) كي لا تلتصقا بشريط التنقل */
    val bottomRaiseDp: Float = 14f,
    /** توقّع الكلمة التالية (من أنماط كتابتك + قائمة مدمجة) */
    val nextWord: Boolean = true,
    /** تدقيق إملائي: اقتراح التصحيح بالشريط لو الكلمة غير موجودة بالقاموس */
    val spellCheck: Boolean = true,
    /** اقتراح إيموجي بشريط الاقتراحات */
    val suggestEmojis: Boolean = true,
    /** تفعيل الاختصارات النصية */
    val textShortcuts: Boolean = true,
    val holdDelay: HoldDelay = HoldDelay.MEDIUM,
    val backspaceSpeed: BackspaceSpeed = BackspaceSpeed.NORMAL,
    val spaceBarAction: SpaceBarAction = SpaceBarAction.CURSOR,
    /** تبقى لوحة الرموز مفتوحة بعد كتابة رمز (لو متوقف ترجع للحروف) */
    val keepSymbolPanel: Boolean = true,
    /** الإيموجي المستخدمة مؤخرًا (محفوظة بين الجلسات) */
    val recentEmojis: List<String> = emptyList(),
    /** اللغات الظاهرة بدورة الكرة الأرضية بالترتيب (الافتراضي: إنكليزي وعربي) */
    val enabledLanguages: List<KeyboardLanguage> = listOf(KeyboardLanguage.ENGLISH, KeyboardLanguage.ARABIC),
    /** حجم اللوحة كله (ارتفاع المفاتيح والخطوط): 0.8 إلى 1.3، والافتراضي 1.0 */
    val keyboardScale: Float = 1f,
    /** سرعة كل حركات اللوحة (تبديل الصفحات، اللغة، ضغط المفاتيح)؛ OFF يلغيها تمامًا */
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val translateTarget: String = "en",
    val translateRecents: List<String> = emptyList(),
    val keyboardTheme: KeyboardTheme = KeyboardTheme.IOS,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val autoCacheClear: Boolean = true,
    /** إخفاء أيقونة التطبيق من الشاشة الرئيسية وشاشة التطبيقات (تبقى لوحة المفاتيح تعمل عاديًا) */
    val hideAppIcon: Boolean = false
)

@Immutable
data class KeyboardUiState(
    val page: KeyboardPage = KeyboardPage.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    /** مستوى صوت الضغط 0..1 */
    val soundVolume: Float = 0.5f,
    val hapticEnabled: Boolean = true,
    val numberRow: Boolean = false,
    val enterKind: EnterKind = EnterKind.RETURN,
    val fieldKind: FieldKind = FieldKind.TEXT,
    /** بدائل الحرف المضغوط مطوّلًا؛ تُعرض بشريط الاقتراحات أعلى اللوحة */
    val alternates: List<String> = emptyList(),
    val englishLayout: EnglishLayout = EnglishLayout.QWERTY,
    val arabicLayout: ArabicLayout = ArabicLayout.STANDARD,
    val style: KeyboardStyle = KeyboardStyle.IOS18,
    val size: KeyboardSize = KeyboardSize.MEDIUM,
    val keySpacing: KeySpacing = KeySpacing.NORMAL,
    val numKeyTarget: NumKeyTarget = NumKeyTarget.SYMBOLS,
    val toolbarItems: List<ToolbarItem> = ToolbarItem.values().toList(),
    val oneHand: OneHandMode = OneHandMode.OFF,
    val suggestionsEnabled: Boolean = true,
    val keyPreview: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val arabicDigits: Boolean = false,
    val swipeTyping: Boolean = true,
    val autoCorrect: Boolean = true,
    val autoSentenceFix: Boolean = true,
    val layoutVariants: Map<String, String> = emptyMap(),
    val emojiFavorites: List<String> = emptyList(),
    val emojiTones: Map<String, String> = emptyMap(),
    val mathResults: Boolean = true,
    val globeOffsetDp: Float = 0f,
    val hideButtonMode: HideButtonMode = HideButtonMode.ARROW,
    val bottomRaiseDp: Float = 14f,
    /** نص نُسخ للتو: يظهر كشريحة لصق سريعة بشريط الاقتراحات لمدة قصيرة */
    val freshClip: String? = null,
    /** اقتراحات الإكمال (بدون الكلمة المكتوبة نفسها) */
    val suggestions: List<String> = emptyList(),
    /** الكلمة الجارية كما كُتبت: تظهر بين علامتي اقتباس بأول خانة (مثل iOS) */
    val literal: String = "",
    val removedWord: String? = null,
    val mathResult: MathResult? = null,
    val toolbarOpen: Boolean = false,
    val clipItems: List<ClipItem> = emptyList(),
    val clipEdit: ClipEdit? = null,
    /** توقّع الكلمة التالية (من أنماط كتابتك + قائمة مدمجة) */
    val nextWord: Boolean = true,
    /** تدقيق إملائي: اقتراح التصحيح بالشريط لو الكلمة غير موجودة بالقاموس */
    val spellCheck: Boolean = true,
    /** اقتراح إيموجي بشريط الاقتراحات */
    val suggestEmojis: Boolean = true,
    /** تفعيل الاختصارات النصية */
    val textShortcuts: Boolean = true,
    val holdDelay: HoldDelay = HoldDelay.MEDIUM,
    val backspaceSpeed: BackspaceSpeed = BackspaceSpeed.NORMAL,
    val spaceBarAction: SpaceBarAction = SpaceBarAction.CURSOR,
    /** تبقى لوحة الرموز مفتوحة بعد كتابة رمز (لو متوقف ترجع للحروف) */
    val keepSymbolPanel: Boolean = true,
    val recentEmojis: List<String> = emptyList(),
    val enabledLanguages: List<KeyboardLanguage> = listOf(KeyboardLanguage.ENGLISH, KeyboardLanguage.ARABIC),
    val keyboardScale: Float = 1f,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val translate: TranslateUi? = null,
    val voice: VoiceUi? = null,
    val emojiSearch: EmojiSearchUi? = null,
    val navButtons: Boolean = false,
    val translateTarget: String = "en",
    val translateRecents: List<String> = emptyList(),
    val keyboardTheme: KeyboardTheme = KeyboardTheme.IOS,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val autoCacheClear: Boolean = true
)


@Immutable
data class TranslateUi(
    val source: String = "",
    val result: String = "",
    val detected: String = "",
    val loading: Boolean = false,
    val error: Boolean = false
)


enum class VoiceStatus { STARTING, LISTENING, PAUSED, NO_PERMISSION, UNAVAILABLE, NETWORK, FAILED }

@Immutable
data class EmojiSearchUi(
    val query: String = "",
    val results: List<String> = emptyList()
)

@Immutable
data class VoiceUi(
    val status: VoiceStatus = VoiceStatus.STARTING,
    val text: String = ""
)

class VoiceOptions(
    val bias: List<String> = emptyList(),
    val rank: ((String) -> Double)? = null,
    val cueVolume: Float = 0f
)
