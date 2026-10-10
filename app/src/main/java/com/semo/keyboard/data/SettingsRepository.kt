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
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.HideButtonMode
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardSize
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** مخزن الحافظة والكلمات المتعلَّمة: بالتخزين المشفّر (CE) كي لا يُقرأ قبل فك قفل الجهاز */
internal val Context.semoDataStore by preferencesDataStore(name = "semo_keyboard_settings")

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

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { s -> runCatching { enumValueOf<T>(s) }.getOrNull() } ?: default

/**
 * مصدر الحقيقة الوحيد للتفضيلات. التطبيق واللوحة (خدمة الـ IME) يشتركان بنفس الـ DataStore،
 * فأي تغيير بالإعدادات ينعكس فورًا على اللوحة.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.semoSettingsStore()

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
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
        val MATH_RESULTS = booleanPreferencesKey("math_results")
        val GLOBE_OFFSET = floatPreferencesKey("globe_offset_dp")
        val HIDE_BUTTON = stringPreferencesKey("hide_button_mode")
        val BOTTOM_RAISE = floatPreferencesKey("bottom_raise_dp")
        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
    }

    val settings: Flow<SemoSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SemoSettings(
                themeMode = p[Keys.THEME_MODE].toEnumOr(ThemeMode.SYSTEM),
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
                oneHand = p[Keys.ONE_HAND].toEnumOr(OneHandMode.OFF),
                suggestionsEnabled = p[Keys.SUGGESTIONS] ?: true,
                keyPreview = p[Keys.KEY_PREVIEW] ?: true,
                autoCapitalize = p[Keys.AUTO_CAPITALIZE] ?: true,
                doubleSpacePeriod = p[Keys.DOUBLE_SPACE] ?: true,
                clipboardEnabled = p[Keys.CLIPBOARD] ?: true,
                arabicDigits = p[Keys.ARABIC_DIGITS] ?: false,
                swipeTyping = p[Keys.SWIPE_TYPING] ?: true,
                autoCorrect = p[Keys.AUTO_CORRECT] ?: true,
                mathResults = p[Keys.MATH_RESULTS] ?: true,
                globeOffsetDp = (p[Keys.GLOBE_OFFSET] ?: 0f).coerceIn(-16f, 16f),
                hideButtonMode = p[Keys.HIDE_BUTTON].toEnumOr(HideButtonMode.ARROW),
                bottomRaiseDp = (p[Keys.BOTTOM_RAISE] ?: 14f).coerceIn(0f, 40f),
                recentEmojis = p[Keys.RECENT_EMOJIS].orEmpty().split(' ').filter { it.isNotEmpty() }
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setSoundEnabled(v: Boolean) { store.edit { it[Keys.SOUND] = v } }
    suspend fun setSoundVolume(v: Float) { store.edit { it[Keys.SOUND_VOLUME] = v.coerceIn(0f, 1f) } }
    suspend fun setHapticEnabled(v: Boolean) { store.edit { it[Keys.HAPTIC] = v } }
    suspend fun setNumberRow(v: Boolean) { store.edit { it[Keys.NUMBER_ROW] = v } }
    suspend fun setLanguage(l: KeyboardLanguage) { store.edit { it[Keys.LANGUAGE] = l.name } }
    suspend fun setOnboardingCompleted(v: Boolean) { store.edit { it[Keys.ONBOARDING] = v } }
    suspend fun setEnglishLayout(v: EnglishLayout) { store.edit { it[Keys.ENGLISH_LAYOUT] = v.name } }
    suspend fun setArabicLayout(v: ArabicLayout) { store.edit { it[Keys.ARABIC_LAYOUT] = v.name } }
    suspend fun setStyle(v: KeyboardStyle) { store.edit { it[Keys.STYLE] = v.name } }
    suspend fun setSize(v: KeyboardSize) { store.edit { it[Keys.SIZE] = v.name } }
    suspend fun setOneHand(v: OneHandMode) { store.edit { it[Keys.ONE_HAND] = v.name } }
    suspend fun setSuggestionsEnabled(v: Boolean) { store.edit { it[Keys.SUGGESTIONS] = v } }
    suspend fun setKeyPreview(v: Boolean) { store.edit { it[Keys.KEY_PREVIEW] = v } }
    suspend fun setAutoCapitalize(v: Boolean) { store.edit { it[Keys.AUTO_CAPITALIZE] = v } }
    suspend fun setDoubleSpacePeriod(v: Boolean) { store.edit { it[Keys.DOUBLE_SPACE] = v } }
    suspend fun setClipboardEnabled(v: Boolean) { store.edit { it[Keys.CLIPBOARD] = v } }
    suspend fun setArabicDigits(v: Boolean) { store.edit { it[Keys.ARABIC_DIGITS] = v } }
    suspend fun setSwipeTyping(v: Boolean) { store.edit { it[Keys.SWIPE_TYPING] = v } }
    suspend fun setAutoCorrect(v: Boolean) { store.edit { it[Keys.AUTO_CORRECT] = v } }
    suspend fun setMathResults(v: Boolean) { store.edit { it[Keys.MATH_RESULTS] = v } }
    suspend fun setGlobeOffset(v: Float) { store.edit { it[Keys.GLOBE_OFFSET] = v.coerceIn(-16f, 16f) } }
    suspend fun setHideButtonMode(v: HideButtonMode) { store.edit { it[Keys.HIDE_BUTTON] = v.name } }
    suspend fun setBottomRaise(v: Float) { store.edit { it[Keys.BOTTOM_RAISE] = v.coerceIn(0f, 40f) } }
    suspend fun setRecentEmojis(list: List<String>) { store.edit { it[Keys.RECENT_EMOJIS] = list.joinToString(" ") } }
}
