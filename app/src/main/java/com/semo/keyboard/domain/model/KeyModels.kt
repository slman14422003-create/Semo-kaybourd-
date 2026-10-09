package com.semo.keyboard.domain.model

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
    data object SwitchLanguage : KeyAction()
    data object Emoji : KeyAction()
    data object Globe : KeyAction()
    data object Hide : KeyAction()
    data object None : KeyAction()
}

/** تعريف مفتاح واحد على اللوحة، مع وزن العرض النسبي */
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
    /** مفتاح بلا خلفية (أيقونة عائمة مثل الإيموجي والكرة الأرضية بالصف السفلي) */
    val plain: Boolean = false
) {
    companion object {
        fun spacer(weight: Float) = KeyDefinition("", KeyAction.None, weight, isSpacer = true)
    }
}

enum class ShiftState { OFF, ON, LOCKED }
enum class KeyboardPage { LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI, CLIPBOARD, EDIT }
enum class KeyboardLanguage { ENGLISH, ARABIC }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** ترتيبات المفاتيح الإنكليزية */
enum class EnglishLayout { QWERTY, AZERTY, QWERTZ }

/** ترتيبات المفاتيح العربية */
enum class ArabicLayout { STANDARD, ALPHABETIC }

/** شكل اللوحة: iOS 26 (الجديد) أو iOS 18 (الكلاسيكي) */
enum class KeyboardStyle { IOS26, IOS18 }

/** ارتفاع المفاتيح */
enum class KeyboardSize { SMALL, MEDIUM, LARGE }

/** وضع اليد الواحدة: اللوحة مزاحة لليسار أو لليمين */
enum class OneHandMode { OFF, LEFT, RIGHT }

/** أدوات التحرير في صفحة التحرير */
enum class EditAction { SELECT_ALL, CUT, COPY, PASTE, LEFT, RIGHT, HOME, END }

/** شكل زر الإدخال حسب نوع الحقل (إدخال عادي، بحث، إرسال ...) */
enum class EnterKind { RETURN, SEARCH, SEND, GO, DONE, NEXT }

/** عنصر من سجل الحافظة */
data class ClipItem(val text: String, val pinned: Boolean)

/** تفضيلات المستخدم المحفوظة */
data class SemoSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val numberRow: Boolean = false,
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val onboardingCompleted: Boolean = false,
    val englishLayout: EnglishLayout = EnglishLayout.QWERTY,
    val arabicLayout: ArabicLayout = ArabicLayout.STANDARD,
    val style: KeyboardStyle = KeyboardStyle.IOS26,
    val size: KeyboardSize = KeyboardSize.MEDIUM,
    val oneHand: OneHandMode = OneHandMode.OFF,
    val suggestionsEnabled: Boolean = true,
    val keyPreview: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val arabicDigits: Boolean = false
)

data class KeyboardUiState(
    val page: KeyboardPage = KeyboardPage.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val numberRow: Boolean = false,
    val enterKind: EnterKind = EnterKind.RETURN,
    /** بدائل الحرف المضغوط مطوّلًا؛ تُعرض بشريط الاقتراحات أعلى اللوحة */
    val alternates: List<String> = emptyList(),
    val englishLayout: EnglishLayout = EnglishLayout.QWERTY,
    val arabicLayout: ArabicLayout = ArabicLayout.STANDARD,
    val style: KeyboardStyle = KeyboardStyle.IOS26,
    val size: KeyboardSize = KeyboardSize.MEDIUM,
    val oneHand: OneHandMode = OneHandMode.OFF,
    val suggestionsEnabled: Boolean = true,
    val keyPreview: Boolean = true,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val arabicDigits: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val toolbarOpen: Boolean = false,
    val clipItems: List<ClipItem> = emptyList(),
    val recentEmojis: List<String> = emptyList()
)
