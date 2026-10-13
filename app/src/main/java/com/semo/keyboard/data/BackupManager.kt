package com.semo.keyboard.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val APP_ID = "semo-keyboard-backup"
    private const val FORMAT = 1
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val excluded = setOf(
        "clipboard_items",
        "onboarding_completed",
        "last_cache_clear"
    )

    data class ImportResult(val ok: Boolean, val keys: Int)

    suspend fun export(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.applicationContext.semoDataStore.data.first()
            val root = JSONObject()
            root.put("app", APP_ID)
            root.put("format", FORMAT)
            root.put("created", System.currentTimeMillis())
            val items = JSONObject()
            for ((key, value) in prefs.asMap()) {
                if (key.name in excluded) continue
                val entry = JSONObject()
                when (value) {
                    is String -> { entry.put("t", "s"); entry.put("v", value) }
                    is Boolean -> { entry.put("t", "b"); entry.put("v", value) }
                    is Int -> { entry.put("t", "i"); entry.put("v", value) }
                    is Long -> { entry.put("t", "l"); entry.put("v", value) }
                    is Float -> { entry.put("t", "f"); entry.put("v", value.toDouble()) }
                    is Double -> { entry.put("t", "d"); entry.put("v", value) }
                    is Set<*> -> { entry.put("t", "z"); entry.put("v", JSONArray(value.map { it.toString() })) }
                    else -> continue
                }
                items.put(key.name, entry)
            }
            root.put("items", items)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(1).toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
    }

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching ImportResult(false, 0)
            if (bytes.size > MAX_BYTES) return@runCatching ImportResult(false, 0)
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optString("app") != APP_ID || root.optInt("format", 0) < 1) return@runCatching ImportResult(false, 0)
            val items = root.getJSONObject("items")
            var count = 0
            context.applicationContext.semoDataStore.edit { prefs ->
                val names = items.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    if (name in excluded || name.length > 80) continue
                    val entry = items.optJSONObject(name) ?: continue
                    if (apply(prefs, name, entry)) count++
                }
            }
            ImportResult(count > 0, count)
        }.getOrDefault(ImportResult(false, 0))
    }

    private fun apply(prefs: androidx.datastore.preferences.core.MutablePreferences, name: String, entry: JSONObject): Boolean {
        return when (entry.optString("t")) {
            "s" -> { prefs[stringPreferencesKey(name)] = entry.getString("v"); true }
            "b" -> { prefs[booleanPreferencesKey(name)] = entry.getBoolean("v"); true }
            "i" -> { prefs[intPreferencesKey(name)] = entry.getInt("v"); true }
            "l" -> { prefs[longPreferencesKey(name)] = entry.getLong("v"); true }
            "f" -> { prefs[floatPreferencesKey(name)] = entry.getDouble("v").toFloat(); true }
            "d" -> { prefs[doublePreferencesKey(name)] = entry.getDouble("v"); true }
            "z" -> {
                val arr = entry.getJSONArray("v")
                prefs[stringSetPreferencesKey(name)] = (0 until arr.length()).map { arr.getString(it) }.toSet()
                true
            }
            else -> false
        }
    }
}
