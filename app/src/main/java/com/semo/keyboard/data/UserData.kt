package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.TextShortcut
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val SEP = "\u001E"
private const val FIELD = "\u001F"

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

/**
 * الكلمات وأزواج الكلمات التي تعلّمتها اللوحة من الكتابة (محلية بالكامل، قابلة للمسح).
 * كل كلمة تُحفظ مع عدد مرات استعمالها فتتقدّم الكلمات الأكثر استعمالًا بالاقتراحات، وأزواج
 * (كلمة ← الكلمة التي تلتها) تُستعمل لتوقّع الكلمة التالية.
 */
class LearnedWordsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.semoDataStore
    private val key = stringPreferencesKey("learned_words")
    private val pairKey = stringPreferencesKey("learned_bigrams")

    /** الأكثر استعمالًا أولًا، ومع التساوي الأحدث أولًا (الترتيب المستقر يحفظ الحداثة) */
    val words: Flow<List<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> decodeWords(p[key]).sortedByDescending { it.second }.map { it.first } }

    /** كلمة (بصيغة مفتاح البحث) ← الكلمات التي تلتها مرتبة بالأكثر تكرارًا */
    val bigrams: Flow<Map<String, List<String>>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> decodePairs(p[pairKey]) }

    suspend fun learn(word: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val w = clean(word)
        if (w.length < 3 || w.length > 30) return
        store.edit { p ->
            val list = decodeWords(p[key]).toMutableList()
            val old = list.indexOfFirst { it.first == w }
            val count = if (old >= 0) list.removeAt(old).second + 1 else 1
            list.add(0, w to count.coerceAtMost(MAX_COUNT))
            while (list.size > MAX_WORDS) {
                val min = list.minOf { it.second }
                list.removeAt(list.indexOfLast { it.second == min })
            }
            p[key] = list.joinToString(SEP) { it.first + FIELD + it.second }
        }
    }

    /** يسجّل أن [next] جاءت بعد [prev] */
    suspend fun learnPair(prev: String, next: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val a = clean(BigDictionary.key(prev))
        val b = clean(next)
        if (a.isEmpty() || a.length > 30 || b.length < 2 || b.length > 30) return
        store.edit { p ->
            val list = decodeRawPairs(p[pairKey]).toMutableList()
            val old = list.indexOfFirst { it.first == a && it.second == b }
            val count = if (old >= 0) list.removeAt(old).third + 1 else 1
            list.add(0, Triple(a, b, count.coerceAtMost(MAX_COUNT)))
            while (list.size > MAX_PAIRS) {
                val min = list.minOf { it.third }
                list.removeAt(list.indexOfLast { it.third == min })
            }
            p[pairKey] = list.joinToString(SEP) { it.first + FIELD + it.second + FIELD + it.third }
        }
    }

    /** يمسح الكلمات المتعلَّمة وأزواج التوقّع معًا */
    suspend fun clear() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            p.remove(key)
            p.remove(pairKey)
        }
    }

    private fun clean(s: String): String = s.trim().replace(SEP, "").replace(FIELD, "")

    /** الصيغة القديمة (كلمة بلا عدّاد) تُقرأ بعدّاد 1 فلا تضيع الكلمات المتعلَّمة سابقًا */
    private fun decodeWords(raw: String?): List<Pair<String, Int>> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).filter { it.isNotEmpty() }.map {
            it.substringBefore(FIELD) to (it.substringAfter(FIELD, "").toIntOrNull() ?: 1)
        }

    private fun decodeRawPairs(raw: String?): List<Triple<String, String, Int>> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).mapNotNull { e ->
            val parts = e.split(FIELD)
            if (parts.size < 3) null else Triple(parts[0], parts[1], parts[2].toIntOrNull() ?: 1)
        }

    private fun decodePairs(raw: String?): Map<String, List<String>> =
        decodeRawPairs(raw)
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { entry -> entry.value.sortedByDescending { it.second }.map { it.first } }

    private companion object {
        const val MAX_WORDS = 1500
        const val MAX_PAIRS = 1200
        const val MAX_COUNT = 1000
    }
}

/** الاختصارات النصية التي يضيفها المستخدم (omw ← On my way!): محلية ومشفّرة مع باقي بيانات المستخدم */
class ShortcutsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.semoDataStore
    private val key = stringPreferencesKey("text_shortcuts")

    val shortcuts: Flow<List<TextShortcut>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> decode(p[key]) }

    /** يرجع false لو الاختصار أو النص غير صالح (الاختصار حروف فقط حتى 15 حرفًا، والنص حتى 200) */
    suspend fun add(abbr: String, expansion: String): Boolean {
        val a = abbr.trim()
        val e = expansion.trim().replace(SEP, " ").replace(FIELD, " ")
        if (a.isEmpty() || a.length > MAX_ABBR || e.isEmpty() || e.length > MAX_EXPANSION) return false
        if (!a.all { it.isLetter() || it == '\'' || it == '’' }) return false
        if (!appContext.isUserStorageUnlocked()) return false
        store.edit { p ->
            val list = decode(p[key]).filterNot { it.abbr.equals(a, ignoreCase = true) }.toMutableList()
            list.add(0, TextShortcut(a, e))
            p[key] = encode(list.take(MAX_SHORTCUTS))
        }
        return true
    }

    suspend fun remove(abbr: String) {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p -> p[key] = encode(decode(p[key]).filterNot { it.abbr == abbr }) }
    }

    private fun encode(list: List<TextShortcut>): String = list.joinToString(SEP) { it.abbr + FIELD + it.expansion }

    private fun decode(raw: String?): List<TextShortcut> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).mapNotNull { e ->
            val abbr = e.substringBefore(FIELD)
            val expansion = e.substringAfter(FIELD, "")
            if (abbr.isEmpty() || expansion.isEmpty()) null else TextShortcut(abbr, expansion)
        }

    private companion object {
        const val MAX_ABBR = 15
        const val MAX_EXPANSION = 200
        const val MAX_SHORTCUTS = 100
    }
}
