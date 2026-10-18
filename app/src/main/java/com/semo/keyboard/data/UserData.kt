package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val SEP = "\u001E"
private const val FIELD = "\u001F"

internal class LearnedEntry(val first: String, val second: String, val permille: Int, val last: Long)

private object ForgettingCurve {
    const val HALF_LIFE_HOURS = 48.0
    const val GAIN = 0.4
    const val WORD_TRUST = 0.7
    const val FIX_TRUST = 0.6
    const val FLOOR = 0.02

    fun nowMinutes(): Long = System.currentTimeMillis() / 60_000L

    fun effective(e: LearnedEntry, now: Long): Double {
        val hours = (now - e.last).coerceAtLeast(0L) / 60.0
        return e.permille / 1000.0 * 0.5.pow(hours / HALF_LIFE_HOURS)
    }

    fun reinforce(current: Double, times: Int): Int {
        var c = current.coerceIn(0.0, 1.0)
        repeat(times.coerceAtLeast(1)) { c += (1.0 - c) * GAIN }
        return (c * 1000).roundToInt().coerceIn(0, 1000)
    }

    fun fromLegacyCount(count: Int): Int = reinforce(0.0, count.coerceIn(1, 20))

    fun prune(list: MutableList<LearnedEntry>, now: Long, max: Int) {
        list.removeAll { effective(it, now) < FLOOR }
        while (list.size > max) {
            var worst = list.size - 1
            var worstValue = effective(list[worst], now)
            for (i in list.size - 2 downTo 0) {
                val v = effective(list[i], now)
                if (v < worstValue) {
                    worst = i
                    worstValue = v
                }
            }
            list.removeAt(worst)
        }
    }
}

private class DecayingKeySet(private val entries: Map<String, LearnedEntry>) : AbstractSet<String>() {
    private var sizeMinute = -1L
    private var sizeValue = 0

    override fun contains(element: String): Boolean {
        val e = entries[element] ?: return false
        return ForgettingCurve.effective(e, ForgettingCurve.nowMinutes()) >= ForgettingCurve.WORD_TRUST
    }

    override val size: Int
        get() {
            val now = ForgettingCurve.nowMinutes()
            if (now != sizeMinute) {
                sizeValue = entries.values.count { ForgettingCurve.effective(it, now) >= ForgettingCurve.WORD_TRUST }
                sizeMinute = now
            }
            return sizeValue
        }

    override fun iterator(): Iterator<String> {
        val now = ForgettingCurve.nowMinutes()
        return entries.filterValues { ForgettingCurve.effective(it, now) >= ForgettingCurve.WORD_TRUST }.keys.iterator()
    }
}

private class DecayingFixMap(private val source: Map<String, LearnedEntry>) : AbstractMap<String, String>() {
    private fun trusted(e: LearnedEntry) =
        ForgettingCurve.effective(e, ForgettingCurve.nowMinutes()) >= ForgettingCurve.FIX_TRUST

    override fun get(key: String): String? = source[key]?.takeIf { trusted(it) }?.second

    override fun containsKey(key: String): Boolean = get(key) != null

    override val entries: Set<Map.Entry<String, String>>
        get() {
            val now = ForgettingCurve.nowMinutes()
            return source.filterValues { ForgettingCurve.effective(it, now) >= ForgettingCurve.FIX_TRUST }
                .mapValues { it.value.second }.entries
        }
}

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
            val raw = sanitize(text).replace("\r\n", "\n").replace('\r', '\n').replace(SEP, " ")
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
            if (index == 0) return@edit
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
                c == '\u0000' || c == '\uFEFF' || c == '\uFFFC' -> {}
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
            val current = decode(p[key])
            if (current.none { it.text == text }) return@edit
            val list = current.map { if (it.text == text) it.copy(pinned = !it.pinned) else it }
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

    suspend fun pruneOrphans() {
        if (!appContext.isUserStorageUnlocked()) return
        val list = items.first()
        if (list.isEmpty()) return
        val textNames = list.filter { it.text.isClipLong() }.map { it.text.clipLongName() }.toSet()
        val imageNames = list.filter { it.text.isClipImage() }.map { it.text.clipImageName() }.toSet()
        withContext(Dispatchers.IO) {
            texts.retain(textNames, ORPHAN_MIN_AGE_MS)
            images.retain(imageNames, ORPHAN_MIN_AGE_MS)
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

    companion object {
        /** أطول من هذا يُحفظ بملف مستقل (الأكواد الطويلة والمقالات) */
        private const val INLINE_MAX = 1500
        /** أقصى طول نص يُحفظ (مليون حرف)؛ الأطول يُتجاهل بدل أن يُلصق مقطوعًا */
        const val MAX_TEXT_LENGTH = 10_000_000
        private const val PREVIEW_LENGTH = 400
        private const val MAX_UNPINNED = 100
        private const val MAX_UNPINNED_LONG = 30
        private const val MAX_UNPINNED_IMAGES = 25
        private const val ORPHAN_MIN_AGE_MS = 10L * 60L * 1000L
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
    private val blockedKey = stringPreferencesKey("blocked_words")
    private val fixesKey = stringPreferencesKey("learned_fixes")

    class BlockSnapshot internal constructor(val word: String, internal val entry: LearnedEntry?, internal val pairs: List<LearnedEntry>)

    val blocked: Flow<List<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[blockedKey] }
        .distinctUntilChanged()
        .map { raw -> raw.orEmpty().split(SEP).filter { it.isNotEmpty() } }

    suspend fun blockWord(word: String): BlockSnapshot? {
        if (!appContext.isUserStorageUnlocked()) return null
        val w = clean(word).lowercase()
        if (w.isEmpty() || w.length > 60) return null
        val k = BigDictionary.key(w)
        var snapshot: BlockSnapshot? = null
        store.edit { p ->
            val blockedList = (p[blockedKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
            blockedList.removeAll { BigDictionary.key(it) == k }
            blockedList.add(0, w)
            while (blockedList.size > MAX_BLOCKED) blockedList.removeAt(blockedList.size - 1)
            p[blockedKey] = blockedList.joinToString(SEP)

            val tokens = k.split(' ').filter { it.isNotEmpty() }
            val words = decodeWords(p[key]).toMutableList()
            val pairs = decodeRawPairs(p[pairKey]).toMutableList()
            var removedEntry: LearnedEntry? = null
            val removedPairs = ArrayList<LearnedEntry>()
            if (tokens.size == 1) {
                removedEntry = words.firstOrNull { BigDictionary.key(it.first) == k }
                words.removeAll { BigDictionary.key(it.first) == k }
                val gone = pairs.filter { pr -> pr.first.split(' ').any { BigDictionary.key(it) == k } || BigDictionary.key(pr.second) == k }
                removedPairs.addAll(gone)
                pairs.removeAll(gone)
            } else {
                for (i in 0 until tokens.size - 1) {
                    val gone = pairs.filter { it.first.split(' ').lastOrNull()?.let { t -> BigDictionary.key(t) } == tokens[i] && BigDictionary.key(it.second) == tokens[i + 1] }
                    removedPairs.addAll(gone)
                    pairs.removeAll(gone)
                }
            }
            p[key] = encodeWords(words)
            p[pairKey] = encodePairs(pairs)
            snapshot = BlockSnapshot(w, removedEntry, removedPairs)
        }
        return snapshot
    }

    suspend fun restore(snapshot: BlockSnapshot) {
        if (!appContext.isUserStorageUnlocked()) return
        val k = BigDictionary.key(snapshot.word)
        store.edit { p ->
            val blockedList = (p[blockedKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
            blockedList.removeAll { BigDictionary.key(it) == k }
            p[blockedKey] = blockedList.joinToString(SEP)
            snapshot.entry?.let { saved ->
                val words = decodeWords(p[key]).toMutableList()
                words.removeAll { BigDictionary.key(it.first) == k }
                words.add(0, LearnedEntry(clean(snapshot.word), "", saved.permille, saved.last))
                p[key] = encodeWords(words)
            }
            if (snapshot.pairs.isNotEmpty()) {
                val pairs = decodeRawPairs(p[pairKey]).toMutableList()
                for (t in snapshot.pairs) {
                    if (pairs.none { it.first == t.first && it.second == t.second }) pairs.add(t)
                }
                p[pairKey] = encodePairs(pairs)
            }
        }
    }

    suspend fun unblockWord(word: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val k = BigDictionary.key(clean(word).lowercase())
        store.edit { p ->
            val list = (p[blockedKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
            list.removeAll { BigDictionary.key(it) == k }
            p[blockedKey] = list.joinToString(SEP)
        }
    }

    suspend fun clearBlocked() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p -> p.remove(blockedKey) }
    }

    val words: Flow<List<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[key] }
        .distinctUntilChanged()
        .map { raw ->
            val now = ForgettingCurve.nowMinutes()
            decodeWords(raw)
                .map { it to ForgettingCurve.effective(it, now) }
                .filter { it.second >= ForgettingCurve.FLOOR }
                .sortedByDescending { it.second }
                .map { it.first.first }
        }

    val trustedWords: Flow<List<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[key] }
        .distinctUntilChanged()
        .map { raw ->
            val now = ForgettingCurve.nowMinutes()
            decodeWords(raw).filter { ForgettingCurve.effective(it, now) >= ForgettingCurve.WORD_TRUST }.map { it.first }
        }

    val trustedKeys: Flow<Set<String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[key] }
        .distinctUntilChanged()
        .map { raw -> DecayingKeySet(decodeWords(raw).associateBy { BigDictionary.key(it.first) }) }

    val learnedFixes: Flow<Map<String, String>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[fixesKey] }
        .distinctUntilChanged()
        .map { raw -> DecayingFixMap(decodeFixes(raw).associateBy { it.first }) }

    suspend fun isTrusted(word: String): Boolean = effectiveTrust(word) >= ForgettingCurve.WORD_TRUST

    suspend fun isKnown(word: String): Boolean = effectiveTrust(word) >= ForgettingCurve.FLOOR

    private suspend fun effectiveTrust(word: String): Double {
        val k = BigDictionary.key(clean(word).lowercase())
        if (k.isEmpty()) return 0.0
        val raw = appContext.credentialPrefsFlow().first()[key]
        val now = ForgettingCurve.nowMinutes()
        return decodeWords(raw).filter { BigDictionary.key(it.first) == k }.maxOfOrNull { ForgettingCurve.effective(it, now) } ?: 0.0
    }

    suspend fun learnFix(typed: String, fixed: String, weight: Int = 1) {
        if (!appContext.isUserStorageUnlocked()) return
        val a = clean(typed).lowercase()
        val b = clean(fixed)
        if (a.length < 3 || a.length > 30 || b.length < 2 || b.length > 30 || a == b.lowercase()) return
        store.edit { p ->
            val now = ForgettingCurve.nowMinutes()
            val list = decodeFixes(p[fixesKey], now).toMutableList()
            val old = list.indexOfFirst { it.first == a }
            val base = if (old >= 0) {
                val prev = list.removeAt(old)
                if (prev.second == b) ForgettingCurve.effective(prev, now) else 0.0
            } else 0.0
            list.add(0, LearnedEntry(a, b, ForgettingCurve.reinforce(base, weight), now))
            ForgettingCurve.prune(list, now, MAX_FIXES)
            p[fixesKey] = encodePairs(list)
        }
    }

    suspend fun forgetFix(typed: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val a = clean(typed).lowercase()
        store.edit { p ->
            val list = decodeFixes(p[fixesKey]).filterNot { it.first == a }
            p[fixesKey] = encodePairs(list)
        }
    }

    private fun decodeFixes(raw: String?, now: Long = ForgettingCurve.nowMinutes()): List<LearnedEntry> =
        decodePairEntries(raw, now).filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

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

    suspend fun unignoreCorrection(original: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val w = clean(original).lowercase()
        if (w.isEmpty()) return
        store.edit { p ->
            val list = (p[ignoredKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()).toMutableSet()
            if (list.remove(w)) p[ignoredKey] = list.joinToString(SEP)
        }
    }

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
            val blockedKeys = (p[blockedKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList())
                .mapTo(HashSet<String>()) { BigDictionary.key(it) }
            if (newWords.isNotEmpty()) {
                val now = ForgettingCurve.nowMinutes()
                val list = decodeWords(p[key], now).toMutableList()
                for (raw in newWords) {
                    val w = clean(raw)
                    if (w.length < 3 || w.length > 30) continue
                    if (blockedKeys.isNotEmpty() && BigDictionary.key(w.lowercase()) in blockedKeys) continue
                    val old = list.indexOfFirst { it.first == w }
                    val base = if (old >= 0) ForgettingCurve.effective(list.removeAt(old), now) else 0.0
                    list.add(0, LearnedEntry(w, "", ForgettingCurve.reinforce(base, 1), now))
                }
                ForgettingCurve.prune(list, now, MAX_WORDS)
                p[key] = encodeWords(list)
            }
            if (newPairs.isNotEmpty()) {
                val now = ForgettingCurve.nowMinutes()
                val list = decodeRawPairs(p[pairKey], now).toMutableList()
                for ((prev, next) in newPairs) {
                    val a = clean(if (prev.contains(' ')) prev else BigDictionary.key(prev))
                    val b = clean(next)
                    if (a.isEmpty() || a.length > 61 || b.length < 2 || b.length > 30) continue
                    if (blockedKeys.isNotEmpty() && (BigDictionary.key(b.lowercase()) in blockedKeys || a.split(' ').any { BigDictionary.key(it) in blockedKeys })) continue
                    val old = list.indexOfFirst { it.first == a && it.second == b }
                    val base = if (old >= 0) ForgettingCurve.effective(list.removeAt(old), now) else 0.0
                    list.add(0, LearnedEntry(a, b, ForgettingCurve.reinforce(base, 1), now))
                }
                ForgettingCurve.prune(list, now, MAX_PAIRS)
                p[pairKey] = encodePairs(list)
            }
        }
    }

    /** يمسح الكلمات المتعلَّمة وأزواج التوقّع معًا */
    suspend fun clear() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            p.remove(key)
            p.remove(pairKey)
            p.remove(fixesKey)
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
            p[key] = encodeWords(list)

            val pairs = decodeRawPairs(p[pairKey]).toMutableList()
            pairs.removeAll { it.first == w || it.second == w }
            p[pairKey] = encodePairs(pairs)
        }
    }

    private fun clean(s: String): String = s.trim().replace(SEP, "").replace(FIELD, "")

    private fun decodeWords(raw: String?, now: Long = ForgettingCurve.nowMinutes()): List<LearnedEntry> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).filter { it.isNotEmpty() }.map { e ->
            val parts = e.split(FIELD)
            if (parts.size >= 3) {
                LearnedEntry(parts[0], "", parts[1].toIntOrNull() ?: 0, parts[2].toLongOrNull() ?: now)
            } else {
                LearnedEntry(parts[0], "", ForgettingCurve.fromLegacyCount(parts.getOrNull(1)?.toIntOrNull() ?: 1), now)
            }
        }

    private fun decodePairEntries(raw: String?, now: Long): List<LearnedEntry> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).mapNotNull { e ->
            val parts = e.split(FIELD)
            when {
                parts.size >= 4 -> LearnedEntry(parts[0], parts[1], parts[2].toIntOrNull() ?: 0, parts[3].toLongOrNull() ?: now)
                parts.size == 3 -> LearnedEntry(parts[0], parts[1], ForgettingCurve.fromLegacyCount(parts[2].toIntOrNull() ?: 1), now)
                else -> null
            }
        }

    private fun decodeRawPairs(raw: String?, now: Long = ForgettingCurve.nowMinutes()): List<LearnedEntry> =
        decodePairEntries(raw, now)

    private fun encodeWords(list: List<LearnedEntry>): String =
        list.joinToString(SEP) { it.first + FIELD + it.permille + FIELD + it.last }

    private fun encodePairs(list: List<LearnedEntry>): String =
        list.joinToString(SEP) { it.first + FIELD + it.second + FIELD + it.permille + FIELD + it.last }

    private fun decodePairs(raw: String?): Map<String, List<String>> {
        val now = ForgettingCurve.nowMinutes()
        return decodeRawPairs(raw, now)
            .map { it to ForgettingCurve.effective(it, now) }
            .filter { it.second >= ForgettingCurve.FLOOR }
            .groupBy({ it.first.first }, { it.first.second to it.second })
            .mapValues { entry -> entry.value.sortedByDescending { it.second }.map { it.first } }
    }

    private companion object {
        const val MAX_WORDS = 2500
        const val MAX_PAIRS = 3000
        const val MAX_IGNORED = 500
        const val MAX_BLOCKED = 2000
        const val MAX_FIXES = 2000
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
