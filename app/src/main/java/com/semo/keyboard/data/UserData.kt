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
import com.semo.keyboard.domain.model.clipLongKey
import com.semo.keyboard.domain.model.clipLongName
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isClipLong
import com.semo.keyboard.domain.model.isImage
import com.semo.keyboard.domain.model.isLongText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val SEP = "\u001E"
private const val FIELD = "\u001F"

/**
 * سجل الحافظة: يُحفظ محليًا فقط، والعناصر المثبّتة لا تُحذف تلقائيًا.
 * يدعم النصوص والروابط والأكواد (القصير بالسجل نفسه، والطويل بملف مستقل عبر [ClipTextStore])
 * والصور (لقطات الشاشة عبر [ClipImageStore]).
 */
class ClipboardRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.semoDataStore
    private val key = stringPreferencesKey("clipboard_items")
    private val images = ClipImageStore(appContext)
    private val texts = ClipTextStore(appContext)

    /**
     * قبل فك قفل الجهاز بعد التشغيل (Direct Boot) تكون القائمة فارغة ولا نكتب شيئًا.
     * نقارن النص الخام أولًا: مخزن التفضيلات يُصدر عند أي تغيير (حتى لو الكلمات المتعلَّمة)،
     * وفك السجل كله بكل مرة كان يبطّئ الكتابة.
     */
    val items: Flow<List<ClipItem>> = appContext.credentialPrefsFlow()
        .map { p -> p[key] }
        .distinctUntilChanged()
        .map { raw -> decode(raw) }

    /**
     * يضيف نصًا (أو مفتاح صورة) للسجل ويرجع المفتاح المخزَّن بالسجل (للصق السريع)، أو null لو رُفض.
     * النص الطويل يُحفظ كاملًا بملف ويُخزَّن بالسجل مفتاح بمعاينة أوله. المسافات البادئة تبقى كما هي (الأكواد).
     */
    private suspend fun prepare(text: String): String? {
        if (!appContext.isUserStorageUnlocked()) return null
        val body = withContext(Dispatchers.Default) {
            val raw = sanitize(text).replace(SEP, " ")
            if (raw.isClipImage()) raw else raw.trimEnd().trimStart('\n', '\r')
        }
        if (body.isBlank()) return null
        return when {
            body.isClipImage() -> body
            body.length > INLINE_MAX -> {
                if (body.length > MAX_TEXT_LENGTH) return null
                val name = withContext(Dispatchers.IO) { texts.save(body) ?: texts.save(body) } ?: return null
                clipLongKey(name, body.length, previewOf(body))
            }
            else -> body
        }
    }

    suspend fun add(text: String): String? {
        val stored = prepare(text) ?: return null
        store.edit { p ->
            val list = decode(p[key]).toMutableList()
            val index = list.indexOfFirst { it.text == stored }
            val pinned = index >= 0 && list[index].pinned
            if (index >= 0) list.removeAt(index)
            list.add(0, ClipItem(stored, pinned))
            val kept = limit(list)
            // الصور والنصوص الطويلة المحذوفة بحدّ العدد تُمسح ملفاتها أيضًا
            list.filter { it !in kept }.forEach { deleteBacking(it.text) }
            p[key] = encode(kept)
        }
        return stored
    }

    /** يستبدل عنصرًا بنص معدَّل في موضعه نفسه مع الإبقاء على التثبيت */
    suspend fun replace(oldStored: String, newText: String): Boolean {
        val stored = prepare(newText) ?: return false
        store.edit { p ->
            val list = decode(p[key]).toMutableList()
            val index = list.indexOfFirst { it.text == oldStored }
            var pinned = index >= 0 && list[index].pinned
            if (index >= 0) list.removeAt(index)
            val dup = list.indexOfFirst { it.text == stored }
            if (dup >= 0) {
                pinned = pinned || list[dup].pinned
                list.removeAt(dup)
            }
            list.add(if (index in 0..list.size) index else 0, ClipItem(stored, pinned))
            val kept = limit(list)
            list.filter { it !in kept }.forEach { deleteBacking(it.text) }
            p[key] = encode(kept)
        }
        if (oldStored != stored) deleteBacking(oldStored)
        return true
    }

    /** يحذف ما يكسر التخزين: أحرف surrogate منفردة، NUL، وBOM */
    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\u0000' || c == '\uFEFF' -> {}
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                    sb.append(c).append(s[i + 1])
                    i++
                }
                c.isSurrogate() -> {}
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /** النص الكامل لعنصر الحافظة: يقرأ ملف النص الطويل، ويرجع النص نفسه لغيره (null لو الملف مفقود) */
    suspend fun fullText(stored: String): String? =
        if (stored.isClipLong()) withContext(Dispatchers.IO) { texts.read(stored.clipLongName()) } else stored

    suspend fun togglePin(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            val list = decode(p[key]).map { if (it.text == text) it.copy(pinned = !it.pinned) else it }
            p[key] = encode(list)
        }
    }

    suspend fun remove(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        deleteBacking(text)
        store.edit { p -> p[key] = encode(decode(p[key]).filterNot { it.text == text }) }
    }

    /** يمسح كل العناصر غير المثبّتة */
    suspend fun clearUnpinned() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            val all = decode(p[key])
            all.filter { !it.pinned }.forEach { deleteBacking(it.text) }
            p[key] = encode(all.filter { it.pinned })
        }
    }

    suspend fun clearAll() {
        if (!appContext.isUserStorageUnlocked()) return
        images.deleteAll()
        texts.deleteAll()
        store.edit { p -> p.remove(key) }
    }

    /** يمسح الملف المرتبط بالعنصر (صورة أو نص طويل)، ولا يفعل شيئًا للنص القصير */
    private fun deleteBacking(text: String) {
        when {
            text.isClipImage() -> images.delete(text.clipImageName())
            text.isClipLong() -> texts.delete(text.clipLongName())
        }
    }

    private fun previewOf(text: String): String {
        val cut = text.take(PREVIEW_LENGTH)
        // لا نقطع زوج surrogate (إيموجي) من المنتصف
        return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    private fun limit(list: List<ClipItem>): List<ClipItem> {
        var unpinned = 0
        var unpinnedImages = 0
        var unpinnedLong = 0
        return list.filter { item ->
            when {
                item.pinned -> true
                item.isImage -> { unpinnedImages++; unpinnedImages <= MAX_UNPINNED_IMAGES }
                item.isLongText -> { unpinnedLong++; unpinnedLong <= MAX_UNPINNED_LONG }
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
        /** أطول من هذا يُحفظ بملف مستقل (الأكواد الطويلة والمقالات) */
        const val INLINE_MAX = 1500
        /** أقصى طول نص يُحفظ (200 ألف حرف)؛ الأطول يُتجاهل بدل أن يُلصق مقطوعًا */
        const val MAX_TEXT_LENGTH = 500_000
        const val PREVIEW_LENGTH = 400
        const val MAX_UNPINNED = 40
        const val MAX_UNPINNED_LONG = 15
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
    private val ignoredKey = stringPreferencesKey("ignored_corrections")

    /** الأكثر استعمالًا أولًا، ومع التساوي الأحدث أولًا (الترتيب المستقر يحفظ الحداثة) */
    val words: Flow<List<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[key] }
        .distinctUntilChanged()
        .map { raw -> decodeWords(raw).sortedByDescending { it.second }.map { it.first } }

    /** كلمة (بصيغة مفتاح البحث) ← الكلمات التي تلتها مرتبة بالأكثر تكرارًا */
    val bigrams: Flow<Map<String, List<String>>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[pairKey] }
        .distinctUntilChanged()
        .map { raw -> decodePairs(raw) }

    /**
     * الكلمات التي رفض المستخدم تصحيحها التلقائي صراحةً (بالرجوع عنه مباشرة بالحذف): اللوحة تتعلّم
     * من هذا الرفض فلا تعيد "تصحيح" نفس الكلمة ثانيةً. مفاتيح بحث (lowercase) لمطابقة بلا حساسية لحالة الأحرف.
     */
    val ignoredCorrections: Flow<Set<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[ignoredKey] }
        .distinctUntilChanged()
        .map { raw -> raw.orEmpty().split(SEP).filter { it.isNotEmpty() }.toSet() }

    /** يسجّل كلمة رفض المستخدم تصحيحها التلقائي (بعد ضغط Backspace فورًا لإرجاعها لأصلها) */
    suspend fun ignoreCorrection(original: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val w = clean(original).lowercase()
        if (w.isEmpty() || w.length > 30) return
        store.edit { p ->
            val list = (p[ignoredKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()).toMutableSet()
            list.add(w)
            while (list.size > MAX_IGNORED) list.remove(list.first())
            p[ignoredKey] = list.joinToString(SEP)
        }
    }

    /**
     * يكتب دفعة كلمات وأزواج بعملية تخزين واحدة (بدل كتابة بكل كلمة): فك السجل وترتيبه وإعادة بناء
     * القوائم بالذاكرة يحصل مرة واحدة كل بضع ثوانٍ، فتخفّ الكتابة على المعالج والبطارية.
     */
    suspend fun learnBatch(newWords: List<String>, newPairs: List<Pair<String, String>>) {
        if (newWords.isEmpty() && newPairs.isEmpty()) return
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            if (newWords.isNotEmpty()) {
                val list = decodeWords(p[key]).toMutableList()
                for (raw in newWords) {
                    val w = clean(raw)
                    if (w.length < 3 || w.length > 30) continue
                    val old = list.indexOfFirst { it.first == w }
                    val count = if (old >= 0) list.removeAt(old).second + 1 else 1
                    list.add(0, w to count.coerceAtMost(MAX_COUNT))
                }
                while (list.size > MAX_WORDS) {
                    val min = list.minOf { it.second }
                    list.removeAt(list.indexOfLast { it.second == min })
                }
                p[key] = list.joinToString(SEP) { it.first + FIELD + it.second }
            }
            if (newPairs.isNotEmpty()) {
                val list = decodeRawPairs(p[pairKey]).toMutableList()
                for ((prev, next) in newPairs) {
                    val a = clean(if (prev.contains(' ')) prev else BigDictionary.key(prev))
                    val b = clean(next)
                    if (a.isEmpty() || a.length > 61 || b.length < 2 || b.length > 30) continue
                    val old = list.indexOfFirst { it.first == a && it.second == b }
                    val count = if (old >= 0) list.removeAt(old).third + 1 else 1
                    list.add(0, Triple(a, b, count.coerceAtMost(MAX_COUNT)))
                }
                while (list.size > MAX_PAIRS) {
                    val min = list.minOf { it.third }
                    list.removeAt(list.indexOfLast { it.third == min })
                }
                p[pairKey] = list.joinToString(SEP) { it.first + FIELD + it.second + FIELD + it.third }
            }
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

    /** يحذف كلمة واحدة فقط (مثلًا كلمة غلط تعلّمتها اللوحة بالخطأ)، مع أي زوج توقّع مرتبط فيها */
    suspend fun removeWord(word: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val w = clean(word)
        if (w.isEmpty()) return
        store.edit { p ->
            val list = decodeWords(p[key]).toMutableList()
            list.removeAll { it.first == w }
            p[key] = list.joinToString(SEP) { it.first + FIELD + it.second }

            val pairs = decodeRawPairs(p[pairKey]).toMutableList()
            pairs.removeAll { it.first == w || it.second == w }
            p[pairKey] = pairs.joinToString(SEP) { it.first + FIELD + it.second + FIELD + it.third }
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
        const val MAX_WORDS = 2500
        const val MAX_PAIRS = 3000
        const val MAX_COUNT = 1000
        const val MAX_IGNORED = 500
    }
}

/** الاختصارات النصية التي يضيفها المستخدم (omw ← On my way!): محلية ومشفّرة مع باقي بيانات المستخدم */
class ShortcutsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.semoDataStore
    private val key = stringPreferencesKey("text_shortcuts")

    val shortcuts: Flow<List<TextShortcut>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[key] }
        .distinctUntilChanged()
        .map { raw -> decode(raw) }

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
