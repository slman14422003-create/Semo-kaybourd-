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
        com.semo.keyboard.ui.keyboard.EmojiBitmapCache.clear()
    }

    /** حجم ما يمكن مسحه: ملفات الكاش على القرص + صور الإيموجي الجاهزة بالذاكرة + بقايا التنزيلات غير المكتملة */
    suspend fun sizeNow(context: Context): Long = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        var total = com.semo.keyboard.ui.keyboard.EmojiBitmapCache.sizeBytes()
        listOfNotNull(app.cacheDir, app.externalCacheDir).forEach { total += dirSize(it) }
        total += leftovers(app).sumOf { dirSize(it) }
        total += com.semo.keyboard.ime.VoskModelManager.leftoverBytes(app)
        total
    }

    /**
     * يمسح الكاش. [force] = true (الضغط اليدوي) يمسح كل شيء فورًا، وبدونه (الدورة التلقائية) تبقى الملفات
     * الأحدث من 30 دقيقة لأنها ممكن تكون قيد الاستعمال.
     */
    suspend fun clearNow(context: Context, force: Boolean = false): Long = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        var freed = com.semo.keyboard.ui.keyboard.EmojiBitmapCache.sizeBytes()
        clearMemory()
        val now = System.currentTimeMillis()
        val minAge = if (force) 0L else MIN_FILE_AGE_MS
        listOfNotNull(app.cacheDir, app.externalCacheDir).forEach { dir -> freed += purge(dir, now, minAge, false) }
        freed += com.semo.keyboard.ime.VoskModelManager.leftoverBytes(app)
        com.semo.keyboard.ime.VoskModelManager.cleanLeftovers(app)
        leftovers(app).forEach { f ->
            val size = dirSize(f)
            if (f.deleteRecursively()) freed += size
        }
        runCatching { app.semoDataStore.edit { it[lastKey] = now } }
        freed
    }

    /** ملفات مؤقتة تركتها تنزيلات القاموس غير المكتملة (لا يدخل فيها التحديثات ولا الملفات الجاهزة) */
    private fun leftovers(app: Context): List<File> {
        val out = ArrayList<File>()
        if (DictionaryManager.activeLanguage.value != null) return out
        listOf("dict", "ngram").forEach { name ->
            File(app.filesDir, name).listFiles()?.forEach { if (it.name.endsWith(".tmp")) out.add(it) }
        }
        return out
    }

    private fun dirSize(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        return f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun isAutoEnabled(context: Context): Boolean =
        runCatching { context.applicationContext.semoDataStore.data.first()[autoKey] ?: true }.getOrDefault(true)

    suspend fun millisUntilNext(context: Context): Long {
        val last = runCatching { context.applicationContext.semoDataStore.data.first()[lastKey] ?: 0L }.getOrDefault(0L)
        val due = last + INTERVAL_MS - System.currentTimeMillis()
        return due.coerceIn(0L, INTERVAL_MS)
    }

    private fun purge(dir: File, now: Long, minAge: Long, removeSelf: Boolean): Long {
        var freed = 0L
        val children = dir.listFiles() ?: return 0L
        for (f in children) {
            if (f.isDirectory) {
                freed += purge(f, now, minAge, true)
            } else if (now - f.lastModified() >= minAge) {
                val size = f.length()
                if (f.delete()) freed += size
            }
        }
        if (removeSelf) runCatching { if (dir.listFiles().isNullOrEmpty()) dir.delete() }
        return freed
    }
}
