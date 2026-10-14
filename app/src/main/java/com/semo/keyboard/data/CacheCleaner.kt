package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.semo.keyboard.domain.logic.NextWordModel
import com.semo.keyboard.domain.logic.SuggestionEngine
import com.semo.keyboard.domain.logic.TranslateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object CacheCleaner {

    const val INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val MIN_FILE_AGE_MS = 30L * 60L * 1000L
    private val lastKey = longPreferencesKey("last_cache_clear")
    val autoKey = booleanPreferencesKey("auto_cache_clear")

    fun clearMemory() {
        SuggestionEngine.clearCaches()
        NextWordModel.clearCaches()
        TranslateEngine.clearCache()
    }

    suspend fun clearNow(context: Context): Long = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        clearMemory()
        var freed = 0L
        val now = System.currentTimeMillis()
        listOfNotNull(app.cacheDir, app.externalCacheDir).forEach { dir -> freed += purge(dir, now, false) }
        runCatching { app.semoDataStore.edit { it[lastKey] = now } }
        freed
    }

    suspend fun isAutoEnabled(context: Context): Boolean =
        runCatching { context.applicationContext.semoDataStore.data.first()[autoKey] ?: true }.getOrDefault(true)

    suspend fun millisUntilNext(context: Context): Long {
        val last = runCatching { context.applicationContext.semoDataStore.data.first()[lastKey] ?: 0L }.getOrDefault(0L)
        val due = last + INTERVAL_MS - System.currentTimeMillis()
        return due.coerceIn(0L, INTERVAL_MS)
    }

    private fun purge(dir: File, now: Long, removeSelf: Boolean): Long {
        var freed = 0L
        val children = dir.listFiles() ?: return 0L
        for (f in children) {
            if (f.isDirectory) {
                freed += purge(f, now, true)
            } else if (now - f.lastModified() >= MIN_FILE_AGE_MS) {
                val size = f.length()
                if (f.delete()) freed += size
            }
        }
        if (removeSelf) runCatching { if (dir.listFiles().isNullOrEmpty()) dir.delete() }
        return freed
    }
}
