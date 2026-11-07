package com.semo.keyboard.data

import android.content.Context
import android.os.UserManager
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.semo.keyboard.domain.model.AnimationSpeed
import com.semo.keyboard.domain.model.AutoCorrectLevel
import com.semo.keyboard.domain.model.BlurHighlight
import com.semo.keyboard.domain.model.BlurPreset
import com.semo.keyboard.domain.model.BlurSource
import com.semo.keyboard.domain.model.BlurTintMode
import com.semo.keyboard.domain.model.LearnSpeed
import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.BackspaceSpeed
import com.semo.keyboard.domain.model.HideButtonMode
import com.semo.keyboard.domain.model.HoldDelay
import com.semo.keyboard.domain.model.SpaceBarAction
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardSize
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.KeySpacing
import com.semo.keyboard.domain.model.NumKeyTarget
import com.semo.keyboard.domain.model.ToolbarItem
import com.semo.keyboard.domain.model.KeyboardTheme
import com.semo.keyboard.domain.model.AppLanguage
import com.semo.keyboard.domain.logic.TranslateLanguages
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** مخزن الحافظة والكلمات المتعلَّمة: بالتخزين المشفّر (CE) كي لا يُقرأ قبل فك قفل الجهاز */
internal val Context.semoDataStore by preferencesDataStore(
    name = "semo_keyboard_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * هل فُكّ قفل المستخدم منذ تشغيل الجهاز؟ قبل ذلك (Direct Boot) التخزين المشفّر غير متاح ولا يجوز لمسه.
 * الخدمة معلَّمة directBootAware كي تعمل اللوحة على شاشة القفل بعد إعادة التشغيل، وبهذا لا تظهر
 * رسالة «بعد إعادة التشغيل لن يعمل التطبيق حتى تفتح القفل» عند تفعيلها.
 */
internal fun Context.isUserStorageUnlocked(): Boolean =
    (getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked ?: true

/**
 * تفضيلات التخزين المشفّر: قبل فك القفل تُصدر قيمًا فارغة (الافتراضية) ثم تنتقل تلقائيًا للبيانات
 * الحقيقية بمجرد فك القفل.
 */
internal fun Context.credentialPrefsFlow(): Flow<Preferences> {
    val app = applicationContext
    return flow {
        if (!app.isUserStorageUnlocked()) {
            emit(emptyPreferences())
            while (!app.isUserStorageUnlocked()) delay(1500)
        }
        emitAll(
            app.semoDataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        )
    }
}

private const val SETTINGS_FILE_NAME = "semo_keyboard_settings"
private val settingsStoreLock = Any()

@Volatile
private var settingsStoreRef: DataStore<Preferences>? = null

/**
 * مخزن الإعدادات (الثيم، اللغة، الترتيب...) بالتخزين المحمي بالجهاز (DE) كي تعمل اللوحة بإعداداتها
 * حتى قبل فك قفل الجهاز. إعدادات النسخ القديمة تُنقل مرة واحدة من التخزين المشفّر، **دون** الحافظة
 * والكلمات المتعلَّمة (تبقى مشفّرة).
 * ملاحظة: مسار الملف يُبنى يدويًا لأن applicationContext لسياق DE يرجع التطبيق نفسه (تخزين CE).
 */
internal fun Context.semoSettingsStore(): DataStore<Preferences> {
    val app = applicationContext
    return settingsStoreRef ?: synchronized(settingsStoreLock) {
        settingsStoreRef ?: PreferenceDataStoreFactory.create(
            migrations = listOf(LegacySettingsMigration(app))
        ) {
            File(app.createDeviceProtectedStorageContext().filesDir, "datastore/$SETTINGS_FILE_NAME.preferences_pb")
        }.also { settingsStoreRef = it }
    }
}

/** ينقل مفاتيح الإعدادات القديمة من مخزن CE إلى مخزن DE (يستثني الحافظة والكلمات المتعلَّمة) */
private class LegacySettingsMigration(private val app: Context) : DataMigration<Preferences> {
    private val excludedKeys = setOf("clipboard_items", "learned_words")

    private fun legacyFile() = File(app.filesDir, "datastore/$SETTINGS_FILE_NAME.preferences_pb")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().isEmpty() && app.isUserStorageUnlocked() && legacyFile().exists()

    @Suppress("UNCHECKED_CAST")
    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = app.semoDataStore.data.first()
        val merged = currentData.toMutablePreferences()
        for ((key, value) in old.asMap()) {
            if (key.name in excludedKeys) continue
            merged[key as Preferences.Key<Any>] = value
        }
        return merged.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

/** عناصر شريط الأدوات المحفوظة (CSV بالترتيب)؛ لو لا شيء محفوظ أو القائمة فارغة نرجع للكل */
private fun decodeToolbar(raw: String?, translateSeen: Boolean, voiceSeen: Boolean): List<ToolbarItem> {
    if (raw == null) return ToolbarItem.values().toList()
    var list = raw.split(',').mapNotNull { n -> runCatching { enumValueOf<ToolbarItem>(n) }.getOrNull() }.distinct()
    if (list.isEmpty()) return ToolbarItem.values().toList()
    if (!translateSeen && ToolbarItem.TRANSLATE !in list) list = list + ToolbarItem.TRANSLATE
    if (!voiceSeen && ToolbarItem.VOICE !in list) list = list + ToolbarItem.VOICE
    // عنصر الحاسبة الجديد: يظهر تلقائيًا لمن لم يحذف أي عنصر من شريطه
    val others = ToolbarItem.values().filter { it != ToolbarItem.CALC && it != ToolbarItem.TRANSLATE && it != ToolbarItem.VOICE }
    if (ToolbarItem.CALC !in list && others.all { it in list }) return list + ToolbarItem.CALC
    return list
}

private fun decodeTones(raw: String?): Map<String, String> =
    raw.orEmpty().split(' ').mapNotNull { pair ->
        val i = pair.indexOf('=')
        if (i > 0 && i < pair.length - 1) pair.substring(0, i) to pair.substring(i + 1) else null
    }.toMap()

private fun decodeVariants(raw: String?): Map<String, String> =
    raw.orEmpty().split(';').mapNotNull { pair ->
        val i = pair.indexOf('=')
        if (i > 0 && i < pair.length - 1) pair.substring(0, i) to pair.substring(i + 1) else null
    }.toMap()

/** اللغات المفعّلة المحفوظة (CSV بالترتيب)؛ لو لا شيء محفوظ نرجع للإنكليزي والعربي */
private fun decodeLanguages(raw: String?): List<KeyboardLanguage> {
    val default = listOf(KeyboardLanguage.ENGLISH, KeyboardLanguage.ARABIC)
    if (raw == null) return default
    val list = raw.split(',').mapNotNull { n -> runCatching { enumValueOf<KeyboardLanguage>(n) }.getOrNull() }.distinct()
    return if (list.isEmpty()) default else list
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { s -> runCatching { enumValueOf<T>(s) }.getOrNull() } ?: default

/**
 * مصدر الحقيقة الوحيد للتفضيلات. التطبيق واللوحة (خدمة الـ IME) يشتركان بنفس الـ DataStore،
 * فأي تغيير بالإعدادات ينعكس فورًا على اللوحة.
 */
class SettingsRepository(context: Context) {

    private val appCtx = context.applicationContext
    private val store = context.applicationContext.semoSettingsStore()

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val KEYBOARD_THEME = stringPreferencesKey("keyboard_theme")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val AUTO_CACHE_CLEAR = booleanPreferencesKey("auto_cache_clear")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val HAPTIC = booleanPreferencesKey("haptic_enabled")
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val ENGLISH_LAYOUT = stringPreferencesKey("english_layout")
        val ARABIC_LAYOUT = stringPreferencesKey("arabic_layout")
        val STYLE = stringPreferencesKey("key_style")
        val SIZE = stringPreferencesKey("key_size")
        val ONE_HAND = stringPreferencesKey("one_hand")
        val SUGGESTIONS = booleanPreferencesKey("suggestions")
        val KEY_PREVIEW = booleanPreferencesKey("key_preview")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE = booleanPreferencesKey("double_space_period")
        val CLIPBOARD = booleanPreferencesKey("clipboard_enabled")
        val ARABIC_DIGITS = booleanPreferencesKey("arabic_digits")
        val SWIPE_TYPING = booleanPreferencesKey("swipe_typing")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val PROTECT_LEARNED = booleanPreferencesKey("protect_learned_words")
        val PROTECT_NAMES = booleanPreferencesKey("protect_names")
        val AC_LEVEL = stringPreferencesKey("auto_correct_level")
        val AC_ARABIC = booleanPreferencesKey("auto_correct_arabic")
        val AC_LATIN = booleanPreferencesKey("auto_correct_latin")
        val AC_MIN_LEN = intPreferencesKey("auto_correct_min_len")
        val AC_PUNCT = booleanPreferencesKey("auto_correct_punct")
        val LEARN_WORDS = booleanPreferencesKey("learn_words_enabled")
        val LEARN_SPEED = stringPreferencesKey("learn_speed")
        val LEARN_NEXT = booleanPreferencesKey("learn_next_words")
        val BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        val BLUR_PRESET = stringPreferencesKey("blur_preset")
        val BLUR_RADIUS = intPreferencesKey("blur_radius")
        val BLUR_TINT = floatPreferencesKey("blur_tint")
        val BLUR_TINT_MODE = stringPreferencesKey("blur_tint_mode")
        val BLUR_KEYS = booleanPreferencesKey("blur_keys")
        val BLUR_BORDER = floatPreferencesKey("blur_border")
        val BLUR_SHEEN = floatPreferencesKey("blur_sheen")
        val BLUR_FALLBACK = booleanPreferencesKey("blur_fallback_solid")
        val BLUR_SOURCE = stringPreferencesKey("blur_source")
        val BLUR_IMAGE_STAMP = longPreferencesKey("blur_image_stamp")
        val BLUR_IMAGE_RADIUS = intPreferencesKey("blur_image_radius")
        val BLUR_IMAGE_DIM = floatPreferencesKey("blur_image_dim")
        val BLUR_IMAGE_SAT = floatPreferencesKey("blur_image_saturation")
        val BLUR_HIGHLIGHT = stringPreferencesKey("blur_highlight")
        val BLUR_EDGE_WIDTH = floatPreferencesKey("blur_edge_width")
        val AUTO_SENTENCE_FIX = booleanPreferencesKey("auto_sentence_fix")
        val AUTO_COMPLETE = booleanPreferencesKey("auto_complete")
        val SMART_WRITING = booleanPreferencesKey("smart_writing")
        val AUTO_PAIRS = booleanPreferencesKey("auto_pairs")
        val SMART_PUNCTUATION = booleanPreferencesKey("smart_punctuation")
        val LAYOUT_VARIANTS = stringPreferencesKey("layout_variants")
        val EMOJI_FAVORITES = stringPreferencesKey("emoji_favorites")
        val EMOJI_TONES = stringPreferencesKey("emoji_tones")
        val MATH_RESULTS = booleanPreferencesKey("math_results")
        val GLOBE_OFFSET = floatPreferencesKey("globe_offset_dp")
        val HIDE_BUTTON = stringPreferencesKey("hide_button_mode")
        val BOTTOM_RAISE = floatPreferencesKey("bottom_raise_dp")
        val BOTTOM_MANUAL = booleanPreferencesKey("bottom_manual_pos")
        val AUTO_FIT = booleanPreferencesKey("auto_fit")
        val GLOBE_DX = floatPreferencesKey("globe_dx_dp")
        val GLOBE_DY = floatPreferencesKey("globe_dy_dp")
        val HIDE_DX = floatPreferencesKey("hide_dx_dp")
        val HIDE_DY = floatPreferencesKey("hide_dy_dp")
        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
        val NEXT_WORD = booleanPreferencesKey("next_word")
        val SPELL_CHECK = booleanPreferencesKey("spell_check")
        val SUGGEST_EMOJIS = booleanPreferencesKey("suggest_emojis")
        val TEXT_SHORTCUTS = booleanPreferencesKey("text_shortcuts_enabled")
        val HOLD_DELAY = stringPreferencesKey("hold_delay")
        val BACKSPACE_SPEED = stringPreferencesKey("backspace_speed")
        val SPACE_ACTION = stringPreferencesKey("space_bar_action")
        val KEEP_SYMBOLS = booleanPreferencesKey("keep_symbol_panel")
        val KEY_SPACING = stringPreferencesKey("key_spacing")
        val NUM_KEY_TARGET = stringPreferencesKey("num_key_target")
        val TOOLBAR_ITEMS = stringPreferencesKey("toolbar_items")
        val ENABLED_LANGUAGES = stringPreferencesKey("enabled_languages")
        val KEYBOARD_SCALE = floatPreferencesKey("keyboard_scale")
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val PANEL_OPACITY = floatPreferencesKey("panel_opacity")
        val ANIMATION_SPEED = stringPreferencesKey("animation_speed")
        val TRANSLATE_TARGET = stringPreferencesKey("translate_target")
        val TRANSLATE_RECENTS = stringPreferencesKey("translate_recents")
        val TOOLBAR_TRANSLATE_SEEN = booleanPreferencesKey("toolbar_translate_seen")
        val TOOLBAR_VOICE_SEEN = booleanPreferencesKey("toolbar_voice_seen")
        val HIDE_APP_ICON = booleanPreferencesKey("hide_app_icon")
    }

    val settings: Flow<SemoSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SemoSettings(
                themeMode = p[Keys.THEME_MODE].toEnumOr(ThemeMode.SYSTEM),
                keyboardTheme = p[Keys.KEYBOARD_THEME].toEnumOr(KeyboardTheme.IOS),
                appLanguage = p[Keys.APP_LANGUAGE].toEnumOr(AppLanguage.SYSTEM),
                autoCacheClear = p[Keys.AUTO_CACHE_CLEAR] ?: true,
                soundEnabled = p[Keys.SOUND] ?: true,
                soundVolume = (p[Keys.SOUND_VOLUME] ?: 0.5f).coerceIn(0f, 1f),
                hapticEnabled = p[Keys.HAPTIC] ?: true,
                numberRow = p[Keys.NUMBER_ROW] ?: false,
                language = p[Keys.LANGUAGE].toEnumOr(KeyboardLanguage.ENGLISH),
                onboardingCompleted = p[Keys.ONBOARDING] ?: false,
                englishLayout = p[Keys.ENGLISH_LAYOUT].toEnumOr(EnglishLayout.QWERTY),
                arabicLayout = p[Keys.ARABIC_LAYOUT].toEnumOr(ArabicLayout.STANDARD),
                style = p[Keys.STYLE].toEnumOr(KeyboardStyle.IOS18),
                size = p[Keys.SIZE].toEnumOr(KeyboardSize.MEDIUM),
                keySpacing = p[Keys.KEY_SPACING].toEnumOr(KeySpacing.NORMAL),
                numKeyTarget = p[Keys.NUM_KEY_TARGET].toEnumOr(NumKeyTarget.SYMBOLS),
                toolbarItems = decodeToolbar(p[Keys.TOOLBAR_ITEMS], p[Keys.TOOLBAR_TRANSLATE_SEEN] ?: false, p[Keys.TOOLBAR_VOICE_SEEN] ?: false),
                oneHand = p[Keys.ONE_HAND].toEnumOr(OneHandMode.OFF),
                suggestionsEnabled = p[Keys.SUGGESTIONS] ?: true,
                keyPreview = p[Keys.KEY_PREVIEW] ?: true,
                autoCapitalize = p[Keys.AUTO_CAPITALIZE] ?: true,
                doubleSpacePeriod = p[Keys.DOUBLE_SPACE] ?: true,
                clipboardEnabled = p[Keys.CLIPBOARD] ?: true,
                arabicDigits = p[Keys.ARABIC_DIGITS] ?: false,
                swipeTyping = p[Keys.SWIPE_TYPING] ?: true,
                autoCorrect = p[Keys.AUTO_CORRECT] ?: true,
                protectLearnedWords = p[Keys.PROTECT_LEARNED] ?: true,
                protectNames = p[Keys.PROTECT_NAMES] ?: true,
                autoCorrectLevel = p[Keys.AC_LEVEL].toEnumOr(AutoCorrectLevel.BALANCED),
                autoCorrectArabic = p[Keys.AC_ARABIC] ?: true,
                autoCorrectLatin = p[Keys.AC_LATIN] ?: true,
                autoCorrectMinLength = (p[Keys.AC_MIN_LEN] ?: 3).coerceIn(2, 6),
                correctOnPunctuation = p[Keys.AC_PUNCT] ?: true,
                learnWordsEnabled = p[Keys.LEARN_WORDS] ?: true,
                learnSpeed = p[Keys.LEARN_SPEED].toEnumOr(LearnSpeed.NORMAL),
                learnNextWords = p[Keys.LEARN_NEXT] ?: true,
                blurEnabled = p[Keys.BLUR_ENABLED] ?: false,
                blurPreset = p[Keys.BLUR_PRESET].toEnumOr(BlurPreset.REGULAR),
                blurRadius = (p[Keys.BLUR_RADIUS] ?: 40).coerceIn(0, 150),
                blurTint = (p[Keys.BLUR_TINT] ?: 0.55f).coerceIn(0f, 1f),
                blurTintMode = p[Keys.BLUR_TINT_MODE].toEnumOr(BlurTintMode.AUTO),
                blurKeys = p[Keys.BLUR_KEYS] ?: true,
                blurBorder = (p[Keys.BLUR_BORDER] ?: 0.5f).coerceIn(0f, 1f),
                blurSheen = (p[Keys.BLUR_SHEEN] ?: 0.3f).coerceIn(0f, 1f),
                blurFallbackSolid = p[Keys.BLUR_FALLBACK] ?: true,
                blurSource = p[Keys.BLUR_SOURCE].toEnumOr(BlurSource.WINDOW),
                blurImageStamp = p[Keys.BLUR_IMAGE_STAMP] ?: 0L,
                blurImageRadius = (p[Keys.BLUR_IMAGE_RADIUS] ?: 20).coerceIn(0, 50),
                blurImageDim = (p[Keys.BLUR_IMAGE_DIM] ?: 0.1f).coerceIn(0f, 0.7f),
                blurImageSaturation = (p[Keys.BLUR_IMAGE_SAT] ?: 1.2f).coerceIn(0.5f, 2f),
                blurHighlight = p[Keys.BLUR_HIGHLIGHT].toEnumOr(BlurHighlight.WHITE),
                blurEdgeWidth = (p[Keys.BLUR_EDGE_WIDTH] ?: 1f).coerceIn(0.5f, 3f),
                autoSentenceFix = p[Keys.AUTO_SENTENCE_FIX] ?: true,
                autoComplete = p[Keys.AUTO_COMPLETE] ?: true,
                smartWriting = p[Keys.SMART_WRITING] ?: true,
                autoPairs = p[Keys.AUTO_PAIRS] ?: false,
                smartPunctuation = p[Keys.SMART_PUNCTUATION] ?: true,
                layoutVariants = decodeVariants(p[Keys.LAYOUT_VARIANTS]),
                emojiFavorites = p[Keys.EMOJI_FAVORITES].orEmpty().split(' ').filter { it.isNotEmpty() },
                emojiTones = decodeTones(p[Keys.EMOJI_TONES]),
                mathResults = p[Keys.MATH_RESULTS] ?: true,
                globeOffsetDp = (p[Keys.GLOBE_OFFSET] ?: 0f).coerceIn(-16f, 16f),
                hideButtonMode = p[Keys.HIDE_BUTTON].toEnumOr(HideButtonMode.ARROW),
                bottomRaiseDp = (p[Keys.BOTTOM_RAISE] ?: 0f).coerceIn(0f, 40f),
                manualBottomPos = p[Keys.BOTTOM_MANUAL] ?: false,
                autoFit = p[Keys.AUTO_FIT] ?: true,
                globeDx = (p[Keys.GLOBE_DX] ?: 0f).coerceIn(0f, 40f),
                globeDy = (p[Keys.GLOBE_DY] ?: 0f).coerceIn(0f, 24f),
                hideDx = (p[Keys.HIDE_DX] ?: 0f).coerceIn(0f, 40f),
                hideDy = (p[Keys.HIDE_DY] ?: 0f).coerceIn(0f, 24f),
                nextWord = p[Keys.NEXT_WORD] ?: true,
                spellCheck = p[Keys.SPELL_CHECK] ?: true,
                suggestEmojis = p[Keys.SUGGEST_EMOJIS] ?: true,
                textShortcuts = p[Keys.TEXT_SHORTCUTS] ?: true,
                holdDelay = p[Keys.HOLD_DELAY].toEnumOr(HoldDelay.MEDIUM),
                backspaceSpeed = p[Keys.BACKSPACE_SPEED].toEnumOr(BackspaceSpeed.NORMAL),
                spaceBarAction = p[Keys.SPACE_ACTION].toEnumOr(SpaceBarAction.CURSOR),
                keepSymbolPanel = p[Keys.KEEP_SYMBOLS] ?: true,
                recentEmojis = p[Keys.RECENT_EMOJIS].orEmpty().split(' ').filter { it.isNotEmpty() },
                enabledLanguages = decodeLanguages(p[Keys.ENABLED_LANGUAGES]),
                keyboardScale = (p[Keys.KEYBOARD_SCALE] ?: 1f).coerceIn(0.8f, 1.3f),
                glassOpacity = (p[Keys.GLASS_OPACITY] ?: 1f).coerceIn(0.35f, 1f),
                panelOpacity = (p[Keys.PANEL_OPACITY] ?: 1f).coerceIn(0f, 1f),
                animationSpeed = p[Keys.ANIMATION_SPEED].toEnumOr(AnimationSpeed.NORMAL),
                translateTarget = p[Keys.TRANSLATE_TARGET] ?: TranslateLanguages.defaultTarget(),
                translateRecents = p[Keys.TRANSLATE_RECENTS]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
                hideAppIcon = p[Keys.HIDE_APP_ICON] ?: false
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setHideAppIcon(v: Boolean) {
        store.edit { it[Keys.HIDE_APP_ICON] = v }
        com.semo.keyboard.util.AppIconVisibility.setIconVisible(appCtx, !v)
    }

    suspend fun syncAppIcon() {
        val hide = store.data.first()[Keys.HIDE_APP_ICON] ?: false
        com.semo.keyboard.util.AppIconVisibility.setIconVisible(appCtx, !hide)
    }
    suspend fun setAppLanguage(language: AppLanguage) { store.edit { it[Keys.APP_LANGUAGE] = language.name } }
    suspend fun setAutoCacheClear(v: Boolean) { store.edit { it[Keys.AUTO_CACHE_CLEAR] = v } }
    suspend fun setKeyboardTheme(theme: KeyboardTheme) { store.edit { it[Keys.KEYBOARD_THEME] = theme.name } }
    suspend fun setSoundEnabled(v: Boolean) { store.edit { it[Keys.SOUND] = v } }
    suspend fun setSoundVolume(v: Float) { store.edit { it[Keys.SOUND_VOLUME] = v.coerceIn(0f, 1f) } }
    suspend fun setHapticEnabled(v: Boolean) { store.edit { it[Keys.HAPTIC] = v } }
    suspend fun setNumberRow(v: Boolean) { store.edit { it[Keys.NUMBER_ROW] = v } }
    suspend fun setLanguage(l: KeyboardLanguage) { store.edit { it[Keys.LANGUAGE] = l.name } }
    suspend fun setOnboardingCompleted(v: Boolean) { store.edit { it[Keys.ONBOARDING] = v } }
    suspend fun setEnglishLayout(v: EnglishLayout) { store.edit { it[Keys.ENGLISH_LAYOUT] = v.name } }
    suspend fun setArabicLayout(v: ArabicLayout) { store.edit { it[Keys.ARABIC_LAYOUT] = v.name } }
    suspend fun setSize(v: KeyboardSize) { store.edit { it[Keys.SIZE] = v.name } }
    suspend fun setKeySpacing(v: KeySpacing) { store.edit { it[Keys.KEY_SPACING] = v.name } }
    suspend fun setNumKeyTarget(v: NumKeyTarget) { store.edit { it[Keys.NUM_KEY_TARGET] = v.name } }
    suspend fun setToolbarItems(v: List<ToolbarItem>) {
        store.edit {
            it[Keys.TOOLBAR_ITEMS] = v.joinToString(",") { item -> item.name }
            it[Keys.TOOLBAR_TRANSLATE_SEEN] = true
            it[Keys.TOOLBAR_VOICE_SEEN] = true
        }
    }
    suspend fun setTranslateTarget(code: String) {
        store.edit {
            it[Keys.TRANSLATE_TARGET] = code
            val old = it[Keys.TRANSLATE_RECENTS]?.split(',')?.filter { c -> c.isNotBlank() && c != code } ?: emptyList()
            it[Keys.TRANSLATE_RECENTS] = (listOf(code) + old).take(6).joinToString(",")
        }
    }
    suspend fun setOneHand(v: OneHandMode) { store.edit { it[Keys.ONE_HAND] = v.name } }
    suspend fun setSuggestionsEnabled(v: Boolean) { store.edit { it[Keys.SUGGESTIONS] = v } }
    suspend fun setKeyPreview(v: Boolean) { store.edit { it[Keys.KEY_PREVIEW] = v } }
    suspend fun setAutoCapitalize(v: Boolean) { store.edit { it[Keys.AUTO_CAPITALIZE] = v } }
    suspend fun setDoubleSpacePeriod(v: Boolean) { store.edit { it[Keys.DOUBLE_SPACE] = v } }
    suspend fun setClipboardEnabled(v: Boolean) { store.edit { it[Keys.CLIPBOARD] = v } }
    suspend fun setArabicDigits(v: Boolean) { store.edit { it[Keys.ARABIC_DIGITS] = v } }
    suspend fun setSwipeTyping(v: Boolean) { store.edit { it[Keys.SWIPE_TYPING] = v } }
    suspend fun setAutoCorrect(v: Boolean) { store.edit { it[Keys.AUTO_CORRECT] = v } }
    suspend fun setProtectLearned(v: Boolean) { store.edit { it[Keys.PROTECT_LEARNED] = v } }
    suspend fun setProtectNames(v: Boolean) { store.edit { it[Keys.PROTECT_NAMES] = v } }
    suspend fun setAutoCorrectLevel(v: AutoCorrectLevel) { store.edit { it[Keys.AC_LEVEL] = v.name } }
    suspend fun setAutoCorrectArabic(v: Boolean) { store.edit { it[Keys.AC_ARABIC] = v } }
    suspend fun setAutoCorrectLatin(v: Boolean) { store.edit { it[Keys.AC_LATIN] = v } }
    suspend fun setAutoCorrectMinLength(v: Int) { store.edit { it[Keys.AC_MIN_LEN] = v.coerceIn(2, 6) } }
    suspend fun setCorrectOnPunctuation(v: Boolean) { store.edit { it[Keys.AC_PUNCT] = v } }
    suspend fun setLearnWords(v: Boolean) { store.edit { it[Keys.LEARN_WORDS] = v } }
    suspend fun setLearnSpeed(v: LearnSpeed) { store.edit { it[Keys.LEARN_SPEED] = v.name } }
    suspend fun setLearnNextWords(v: Boolean) { store.edit { it[Keys.LEARN_NEXT] = v } }
    suspend fun setBlurEnabled(v: Boolean) { store.edit { it[Keys.BLUR_ENABLED] = v } }
    suspend fun setBlurRadius(v: Int) { store.edit { it[Keys.BLUR_RADIUS] = v.coerceIn(0, 150); it[Keys.BLUR_PRESET] = BlurPreset.CUSTOM.name } }
    suspend fun setBlurTint(v: Float) { store.edit { it[Keys.BLUR_TINT] = v.coerceIn(0f, 1f); it[Keys.BLUR_PRESET] = BlurPreset.CUSTOM.name } }
    suspend fun setBlurTintMode(v: BlurTintMode) { store.edit { it[Keys.BLUR_TINT_MODE] = v.name } }
    suspend fun setBlurKeys(v: Boolean) { store.edit { it[Keys.BLUR_KEYS] = v } }
    suspend fun setBlurBorder(v: Float) { store.edit { it[Keys.BLUR_BORDER] = v.coerceIn(0f, 1f); it[Keys.BLUR_PRESET] = BlurPreset.CUSTOM.name } }
    suspend fun setBlurSheen(v: Float) { store.edit { it[Keys.BLUR_SHEEN] = v.coerceIn(0f, 1f); it[Keys.BLUR_PRESET] = BlurPreset.CUSTOM.name } }
    suspend fun setBlurFallbackSolid(v: Boolean) { store.edit { it[Keys.BLUR_FALLBACK] = v } }
    suspend fun setBlurSource(v: BlurSource) { store.edit { it[Keys.BLUR_SOURCE] = v.name } }
    suspend fun setBlurImageStamp(v: Long) { store.edit { it[Keys.BLUR_IMAGE_STAMP] = v } }
    suspend fun setBlurImageRadius(v: Int) { store.edit { it[Keys.BLUR_IMAGE_RADIUS] = v.coerceIn(0, 50) } }
    suspend fun setBlurImageDim(v: Float) { store.edit { it[Keys.BLUR_IMAGE_DIM] = v.coerceIn(0f, 0.7f) } }
    suspend fun setBlurImageSaturation(v: Float) { store.edit { it[Keys.BLUR_IMAGE_SAT] = v.coerceIn(0.5f, 2f) } }
    suspend fun setBlurHighlight(v: BlurHighlight) { store.edit { it[Keys.BLUR_HIGHLIGHT] = v.name } }
    suspend fun setBlurEdgeWidth(v: Float) { store.edit { it[Keys.BLUR_EDGE_WIDTH] = v.coerceIn(0.5f, 3f) } }
    suspend fun applyBlurPreset(v: BlurPreset) {
        store.edit {
            it[Keys.BLUR_PRESET] = v.name
            it[Keys.BLUR_ENABLED] = v != BlurPreset.CUSTOM || it[Keys.BLUR_ENABLED] == true
            when (v) {
                BlurPreset.CLEAR -> { it[Keys.BLUR_RADIUS] = 14; it[Keys.BLUR_TINT] = 0.18f; it[Keys.BLUR_BORDER] = 0.35f; it[Keys.BLUR_SHEEN] = 0.15f; it[Keys.GLASS_OPACITY] = 0.55f; it[Keys.PANEL_OPACITY] = 0.2f }
                BlurPreset.THIN -> { it[Keys.BLUR_RADIUS] = 28; it[Keys.BLUR_TINT] = 0.38f; it[Keys.BLUR_BORDER] = 0.45f; it[Keys.BLUR_SHEEN] = 0.22f; it[Keys.GLASS_OPACITY] = 0.7f; it[Keys.PANEL_OPACITY] = 0.4f }
                BlurPreset.REGULAR -> { it[Keys.BLUR_RADIUS] = 40; it[Keys.BLUR_TINT] = 0.55f; it[Keys.BLUR_BORDER] = 0.5f; it[Keys.BLUR_SHEEN] = 0.3f; it[Keys.GLASS_OPACITY] = 0.8f; it[Keys.PANEL_OPACITY] = 0.6f }
                BlurPreset.THICK -> { it[Keys.BLUR_RADIUS] = 80; it[Keys.BLUR_TINT] = 0.72f; it[Keys.BLUR_BORDER] = 0.55f; it[Keys.BLUR_SHEEN] = 0.35f; it[Keys.GLASS_OPACITY] = 0.9f; it[Keys.PANEL_OPACITY] = 0.8f }
                BlurPreset.CUSTOM -> Unit
            }
        }
    }
    suspend fun setAutoSentenceFix(v: Boolean) { store.edit { it[Keys.AUTO_SENTENCE_FIX] = v } }
    suspend fun setAutoComplete(v: Boolean) { store.edit { it[Keys.AUTO_COMPLETE] = v } }
    suspend fun setSmartWriting(v: Boolean) { store.edit { it[Keys.SMART_WRITING] = v } }
    suspend fun setAutoPairs(v: Boolean) { store.edit { it[Keys.AUTO_PAIRS] = v } }
    suspend fun setSmartPunctuation(v: Boolean) { store.edit { it[Keys.SMART_PUNCTUATION] = v } }
    suspend fun setEmojiFavorites(list: List<String>) { store.edit { it[Keys.EMOJI_FAVORITES] = list.joinToString(" ") } }
    suspend fun setEmojiTone(base: String, variant: String) {
        store.edit {
            val map = decodeTones(it[Keys.EMOJI_TONES]) + (base to variant)
            it[Keys.EMOJI_TONES] = map.entries.joinToString(" ") { e -> e.key + "=" + e.value }
        }
    }
    suspend fun setLayoutVariant(code: String, id: String) {
        store.edit {
            val map = decodeVariants(it[Keys.LAYOUT_VARIANTS]) + (code to id)
            it[Keys.LAYOUT_VARIANTS] = map.entries.joinToString(";") { e -> e.key + "=" + e.value }
        }
    }
    suspend fun setMathResults(v: Boolean) { store.edit { it[Keys.MATH_RESULTS] = v } }
    suspend fun setGlobeOffset(v: Float) { store.edit { it[Keys.GLOBE_OFFSET] = v.coerceIn(-16f, 16f) } }
    suspend fun setHideButtonMode(v: HideButtonMode) { store.edit { it[Keys.HIDE_BUTTON] = v.name } }
    suspend fun setManualBottomPos(v: Boolean) { store.edit { it[Keys.BOTTOM_MANUAL] = v } }
    suspend fun setAutoFit(v: Boolean) { store.edit { it[Keys.AUTO_FIT] = v } }

    suspend fun setBottomPositions(globeDx: Float, globeDy: Float, hideDx: Float, hideDy: Float) {
        store.edit {
            it[Keys.GLOBE_DX] = globeDx.coerceIn(0f, 40f)
            it[Keys.GLOBE_DY] = globeDy.coerceIn(0f, 24f)
            it[Keys.HIDE_DX] = hideDx.coerceIn(0f, 40f)
            it[Keys.HIDE_DY] = hideDy.coerceIn(0f, 24f)
        }
    }

    suspend fun setBottomRaise(v: Float) { store.edit { it[Keys.BOTTOM_RAISE] = v.coerceIn(0f, 40f) } }
    suspend fun setRecentEmojis(list: List<String>) { store.edit { it[Keys.RECENT_EMOJIS] = list.joinToString(" ") } }
    suspend fun setNextWord(v: Boolean) { store.edit { it[Keys.NEXT_WORD] = v } }
    suspend fun setSpellCheck(v: Boolean) { store.edit { it[Keys.SPELL_CHECK] = v } }
    suspend fun setSuggestEmojis(v: Boolean) { store.edit { it[Keys.SUGGEST_EMOJIS] = v } }
    suspend fun setTextShortcuts(v: Boolean) { store.edit { it[Keys.TEXT_SHORTCUTS] = v } }
    suspend fun setHoldDelay(v: HoldDelay) { store.edit { it[Keys.HOLD_DELAY] = v.name } }
    suspend fun setBackspaceSpeed(v: BackspaceSpeed) { store.edit { it[Keys.BACKSPACE_SPEED] = v.name } }
    suspend fun setSpaceBarAction(v: SpaceBarAction) { store.edit { it[Keys.SPACE_ACTION] = v.name } }
    suspend fun setKeepSymbolPanel(v: Boolean) { store.edit { it[Keys.KEEP_SYMBOLS] = v } }
    suspend fun setKeyboardScale(v: Float) { store.edit { it[Keys.KEYBOARD_SCALE] = v.coerceIn(0.8f, 1.3f) } }
    suspend fun setPanelOpacity(v: Float) { store.edit { it[Keys.PANEL_OPACITY] = v.coerceIn(0f, 1f) } }
    suspend fun setGlassOpacity(v: Float) { store.edit { it[Keys.GLASS_OPACITY] = v.coerceIn(0.35f, 1f) } }
    suspend fun setAnimationSpeed(v: AnimationSpeed) { store.edit { it[Keys.ANIMATION_SPEED] = v.name } }

    /** اللغات التي تدور عليها الكرة الأرضية؛ لا تُترك القائمة فارغة أبدًا */
    suspend fun setEnabledLanguages(v: List<KeyboardLanguage>) {
        val list = v.distinct().ifEmpty { listOf(KeyboardLanguage.ENGLISH) }
        store.edit { p ->
            p[Keys.ENABLED_LANGUAGES] = list.joinToString(",") { it.name }
            // لو اللغة الحالية انحذفت من القائمة ننتقل لأولى اللغات المفعّلة
            val current = p[Keys.LANGUAGE].toEnumOr(KeyboardLanguage.ENGLISH)
            if (current !in list) p[Keys.LANGUAGE] = list.first().name
        }
    }

    /** يعيد كل الإعدادات للافتراضي، ويُبقي إكمال الدليل واللغة الحالية والإيموجي الأخيرة */
    suspend fun resetToDefaults() {
        store.edit { p ->
            val onboarding = p[Keys.ONBOARDING]
            val language = p[Keys.LANGUAGE]
            val recent = p[Keys.RECENT_EMOJIS]
            val appLanguage = p[Keys.APP_LANGUAGE]
            val keep = KEEP_ON_RESET.mapNotNull { name -> p.asMap().entries.firstOrNull { it.key.name == name }?.let { it.key to it.value } }
            p.clear()
            for ((k, v) in keep) {
                @Suppress("UNCHECKED_CAST")
                p[k as Preferences.Key<Any>] = v
            }
            if (appLanguage != null) p[Keys.APP_LANGUAGE] = appLanguage
            if (onboarding != null) p[Keys.ONBOARDING] = onboarding
            if (language != null) p[Keys.LANGUAGE] = language
            if (recent != null) p[Keys.RECENT_EMOJIS] = recent
        }
    }

    private companion object {
        val KEEP_ON_RESET = setOf(
            "learned_words", "learned_bigrams", "ignored_corrections", "learned_fixes", "learn_candidates", "rank_choices", "rank_profiles", "blocked_words", "text_shortcuts", "clipboard_items", "last_cache_clear"
        )
    }
}
