package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.semoDataStore by preferencesDataStore(name = "semo_keyboard_settings")

/**
 * مصدر الحقيقة الوحيد للتفضيلات. التطبيق واللوحة (خدمة الـ IME) يشتركان بنفس الـ DataStore،
 * فأي تغيير بالإعدادات ينعكس فورًا على اللوحة.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.semoDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTIC = booleanPreferencesKey("haptic_enabled")
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
    }

    val settings: Flow<SemoSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SemoSettings(
                themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                soundEnabled = p[Keys.SOUND] ?: true,
                hapticEnabled = p[Keys.HAPTIC] ?: true,
                numberRow = p[Keys.NUMBER_ROW] ?: false,
                language = p[Keys.LANGUAGE]?.let { runCatching { KeyboardLanguage.valueOf(it) }.getOrNull() } ?: KeyboardLanguage.ENGLISH,
                onboardingCompleted = p[Keys.ONBOARDING] ?: false
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setSoundEnabled(v: Boolean) { store.edit { it[Keys.SOUND] = v } }
    suspend fun setHapticEnabled(v: Boolean) { store.edit { it[Keys.HAPTIC] = v } }
    suspend fun setNumberRow(v: Boolean) { store.edit { it[Keys.NUMBER_ROW] = v } }
    suspend fun setLanguage(l: KeyboardLanguage) { store.edit { it[Keys.LANGUAGE] = l.name } }
    suspend fun setOnboardingCompleted(v: Boolean) { store.edit { it[Keys.ONBOARDING] = v } }
}
