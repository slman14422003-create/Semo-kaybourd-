package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val SEP = "\u001E"

/** سجل الحافظة: يُحفظ محليًا فقط، والعناصر المثبّتة لا تُحذف تلقائيًا */
class ClipboardRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.semoDataStore
    private val key = stringPreferencesKey("clipboard_items")
    private val images = ClipImageStore(appContext)

    /** قبل فك قفل الجهاز بعد التشغيل (Direct Boot) تكون القائمة فارغة ولا نكتب شيئًا */
    val items: Flow<List<ClipItem>> = appContext.credentialPrefsFlow()
        .map { p -> decode(p[key]) }

    suspend fun add(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val t = text.trim().replace(SEP, " ")
        if (t.isEmpty() || t.length > MAX_LENGTH) return
        store.edit { p ->
            val list = decode(p[key]).toMutableList()
            val index = list.indexOfFirst { it.text == t }
            val pinned = index >= 0 && list[index].pinned
            if (index >= 0) list.removeAt(index)
            list.add(0, ClipItem(t, pinned))
            val kept = limit(list)
            // الصور المحذوفة بحدّ العدد تُمسح ملفاتها أيضًا
            list.filter { it.isImage && it !in kept }.forEach { images.delete(it.text.clipImageName()) }
            p[key] = encode(kept)
        }
    }

    suspend fun togglePin(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            val list = decode(p[key]).map { if (it.text == text) it.copy(pinned = !it.pinned) else it }
            p[key] = encode(list)
        }
    }

    suspend fun remove(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        if (text.isClipImage()) images.delete(text.clipImageName())
        store.edit { p -> p[key] = encode(decode(p[key]).filterNot { it.text == text }) }
    }

    /** يمسح كل العناصر غير المثبّتة */
    suspend fun clearUnpinned() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            val all = decode(p[key])
            all.filter { !it.pinned && it.isImage }.forEach { images.delete(it.text.clipImageName()) }
            p[key] = encode(all.filter { it.pinned })
        }
    }

    suspend fun clearAll() {
        if (!appContext.isUserStorageUnlocked()) return
        images.deleteAll()
        store.edit { p -> p.remove(key) }
    }

    private fun limit(list: List<ClipItem>): List<ClipItem> {
        var unpinned = 0
        var unpinnedImages = 0
        return list.filter { item ->
            when {
                item.pinned -> true
                item.isImage -> { unpinnedImages++; unpinnedImages <= MAX_UNPINNED_IMAGES }
                else -> { unpinned++; unpinned <= MAX_UNPINNED }
            }
        }
    }

    private fun encode(list: List<ClipItem>): String =
        list.joinToString(SEP) { (if (it.pinned) "1" else "0") + it.text }

    private fun decode(raw: String?): List<ClipItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(SEP)
            .filter { it.length > 1 }
            .map { ClipItem(text = it.substring(1), pinned = it[0] == '1') }
    }

    private companion object {
        const val MAX_LENGTH = 5000
        const val MAX_UNPINNED = 30
        const val MAX_UNPINNED_IMAGES = 10
    }
}

/** الكلمات التي تعلّمتها اللوحة من الكتابة (محلية بالكامل، قابلة للمسح) */
class LearnedWordsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.semoDataStore
    private val key = stringPreferencesKey("learned_words")

    val words: Flow<List<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> decode(p[key]) }

    suspend fun learn(word: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val w = word.trim().replace(SEP, "")
        if (w.length < 3 || w.length > 30) return
        store.edit { p ->
            val list = decode(p[key]).toMutableList()
            list.remove(w)
            list.add(0, w)
            p[key] = list.take(MAX_WORDS).joinToString(SEP)
        }
    }

    suspend fun clear() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p -> p.remove(key) }
    }

    private fun decode(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).filter { it.isNotEmpty() }

    private companion object {
        const val MAX_WORDS = 400
    }
}
