package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.semo.keyboard.domain.model.ClipItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val SEP = "\u001E"

/** سجل الحافظة: يُحفظ محليًا فقط، والعناصر المثبّتة لا تُحذف تلقائيًا */
class ClipboardRepository(context: Context) {

    private val store = context.applicationContext.semoDataStore
    private val key = stringPreferencesKey("clipboard_items")

    val items: Flow<List<ClipItem>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> decode(p[key]) }

    suspend fun add(text: String) {
        val t = text.trim().replace(SEP, " ")
        if (t.isEmpty() || t.length > MAX_LENGTH) return
        store.edit { p ->
            val list = decode(p[key]).toMutableList()
            val index = list.indexOfFirst { it.text == t }
            val pinned = index >= 0 && list[index].pinned
            if (index >= 0) list.removeAt(index)
            list.add(0, ClipItem(t, pinned))
            p[key] = encode(limit(list))
        }
    }

    suspend fun togglePin(text: String) {
        store.edit { p ->
            val list = decode(p[key]).map { if (it.text == text) it.copy(pinned = !it.pinned) else it }
            p[key] = encode(list)
        }
    }

    suspend fun remove(text: String) {
        store.edit { p -> p[key] = encode(decode(p[key]).filterNot { it.text == text }) }
    }

    /** يمسح كل العناصر غير المثبّتة */
    suspend fun clearUnpinned() {
        store.edit { p -> p[key] = encode(decode(p[key]).filter { it.pinned }) }
    }

    suspend fun clearAll() {
        store.edit { p -> p.remove(key) }
    }

    private fun limit(list: List<ClipItem>): List<ClipItem> {
        var unpinned = 0
        return list.filter { item ->
            if (item.pinned) true else { unpinned++; unpinned <= MAX_UNPINNED }
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
    }
}

/** الكلمات التي تعلّمتها اللوحة من الكتابة (محلية بالكامل، قابلة للمسح) */
class LearnedWordsRepository(context: Context) {

    private val store = context.applicationContext.semoDataStore
    private val key = stringPreferencesKey("learned_words")

    val words: Flow<List<String>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p: Preferences -> decode(p[key]) }

    suspend fun learn(word: String) {
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
        store.edit { p -> p.remove(key) }
    }

    private fun decode(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).filter { it.isNotEmpty() }

    private companion object {
        const val MAX_WORDS = 400
    }
}
