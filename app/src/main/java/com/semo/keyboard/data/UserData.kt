package com.semo.keyboard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.TextShortcut
import com.semo.keyboard.domain.model.clipFileName
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.model.clipLongKey
import com.semo.keyboard.domain.model.clipLongName
import com.semo.keyboard.domain.model.isClipFile
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isClipLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val SEP = "\u001E"
private const val FIELD = "\u001F"

internal class LearnedEntry(val first: String, val second: String, val permille: Int, val last: Long, val uses: Int = 1)

private object ForgettingCurve {
    const val HALF_LIFE_HOURS = 48.0
    const val GAIN = 0.4
    const val WORD_TRUST = 0.7
    const val FIX_TRUST = 0.6
    const val FLOOR = 0.02
    const val MAX_STAGE = 5

    fun halfLifeHours(uses: Int): Double = HALF_LIFE_HOURS * 2.0.pow((uses - 1).coerceIn(0, MAX_STAGE).toDouble())

    fun estimateUses(permille: Int): Int {
        val p = (permille / 1000.0).coerceIn(0.0, 0.995)
        if (p <= 0.0) return 1
        return (ln(1.0 - p) / ln(1.0 - GAIN)).roundToInt().coerceIn(1, 12)
    }

    fun nowMinutes(): Long = System.currentTimeMillis() / 60_000L

    fun effective(e: LearnedEntry, now: Long): Double {
        val hours = (now - e.last).coerceAtLeast(0L) / 60.0
        return e.permille / 1000.0 * 0.5.pow(hours / halfLifeHours(e.uses))
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
    private val legacyKey = stringPreferencesKey("clipboard_items")
    private val db by lazy { ClipDb(appContext) }
    private val images = ClipImageStore(appContext)
    private val texts = ClipTextStore(appContext)
    private val files = ClipFileStore(appContext)

    val items: Flow<List<ClipItem>> = flow {
        if (!appContext.isUserStorageUnlocked()) {
            emit(emptyList())
            while (!appContext.isUserStorageUnlocked()) delay(1500)
        }
        migrateLegacy()
        emitAll(
            changes.map {
                withContext(Dispatchers.IO) { runCatching { db.all(LIST_LIMIT) }.getOrDefault(emptyList()) }
            }
        )
    }.distinctUntilChanged()

    private suspend fun migrateLegacy() {
        if (migrated.get()) return
        val raw = runCatching { store.data.first()[legacyKey] }.getOrNull()
        if (!raw.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                val legacy = raw.split(SEP).filter { it.length > 1 }.map { ClipItem(it.substring(1), it[0] == '1') }
                var ts = System.currentTimeMillis() - legacy.size
                legacy.asReversed().forEach { item ->
                    val (kind, bytes) = describe(item.text)
                    runCatching { db.upsert(item.text, kind, bytes, item.pinned, ts) }
                    ts++
                }
            }
            runCatching { store.edit { it.remove(legacyKey) } }
        }
        migrated.set(true)
    }

    private fun describe(key: String): Pair<Int, Long> = when {
        key.isClipImage() -> KIND_IMAGE to images.file(key.clipImageName()).length()
        key.isClipLong() -> KIND_LONG to texts.sizeOf(key.clipLongName())
        key.isClipFile() -> KIND_FILE to files.sizeOf(key.clipFileName())
        else -> KIND_TEXT to key.length * 2L
    }

    private suspend fun prepare(text: String): String? {
        if (!appContext.isUserStorageUnlocked()) return null
        val body = withContext(Dispatchers.Default) {
            val raw = sanitize(text).replace("\r\n", "\n").replace('\r', '\n').replace(SEP, " ")
            if (raw.isClipImage() || raw.isClipFile()) raw else raw.trimEnd().trimStart('\n', '\r')
        }
        if (body.isBlank()) return null
        return when {
            body.isClipImage() || body.isClipFile() -> body
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
        return withContext(Dispatchers.IO) {
            runCatching {
                if (db.newestKey() == stored) return@runCatching stored
                val (kind, bytes) = describe(stored)
                db.upsert(stored, kind, bytes)
                trim()
                changes.update { it + 1 }
                stored
            }.getOrNull()
        }
    }

    suspend fun replace(oldStored: String, newText: String): Boolean {
        val stored = prepare(newText) ?: return false
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val ts = db.tsOf(oldStored) ?: System.currentTimeMillis()
                val pinned = db.isPinned(oldStored) || db.isPinned(stored)
                if (oldStored != stored) db.delete(oldStored)
                val (kind, bytes) = describe(stored)
                db.upsert(stored, kind, bytes, pinned, ts)
                trim()
                changes.update { it + 1 }
                true
            }.getOrDefault(false)
        }
        if (ok && oldStored != stored) deleteBacking(oldStored)
        return ok
    }

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

    suspend fun fullText(stored: String): String? =
        if (stored.isClipLong()) withContext(Dispatchers.IO) { texts.read(stored.clipLongName()) } else stored

    suspend fun togglePin(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        withContext(Dispatchers.IO) {
            runCatching {
                if (!db.exists(text)) return@runCatching
                db.setPinned(text, !db.isPinned(text))
                changes.update { it + 1 }
            }
        }
    }

    suspend fun remove(text: String) {
        if (!appContext.isUserStorageUnlocked()) return
        withContext(Dispatchers.IO) {
            runCatching {
                db.delete(text)
                deleteBacking(text)
                changes.update { it + 1 }
            }
        }
    }

    suspend fun clearUnpinned() {
        if (!appContext.isUserStorageUnlocked()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val drop = db.rows().filter { !it.pinned }
                db.deleteKeys(drop.map { it.key })
                drop.forEach { deleteBacking(it.key) }
                changes.update { it + 1 }
            }
        }
    }

    suspend fun pruneOrphans() {
        if (!appContext.isUserStorageUnlocked()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val keys = db.rows().map { it.key }
                if (keys.isEmpty()) return@runCatching
                texts.retain(keys.filter { it.isClipLong() }.map { it.clipLongName() }.toSet(), ORPHAN_MIN_AGE_MS)
                images.retain(keys.filter { it.isClipImage() }.map { it.clipImageName() }.toSet(), ORPHAN_MIN_AGE_MS)
                files.retain(keys.filter { it.isClipFile() }.map { it.clipFileName() }.toSet(), ORPHAN_MIN_AGE_MS)
            }
        }
    }

    suspend fun clearAll() {
        if (!appContext.isUserStorageUnlocked()) return
        withContext(Dispatchers.IO) {
            runCatching {
                images.deleteAll()
                texts.deleteAll()
                files.deleteAll()
                db.clear()
                changes.update { it + 1 }
            }
        }
    }

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { db.rows().filter { it.kind != KIND_TEXT }.sumOf { it.bytes } }.getOrDefault(0L)
    }

    private fun trim() {
        val rows = db.rows()
        var unpinned = 0
        var unpinnedLong = 0
        var unpinnedImages = 0
        var unpinnedFiles = 0
        val drop = ArrayList<ClipDb.Row>()
        val kept = ArrayList<ClipDb.Row>()
        for (r in rows) {
            if (r.pinned) {
                kept.add(r)
                continue
            }
            val ok = when (r.kind) {
                KIND_IMAGE -> ++unpinnedImages <= MAX_UNPINNED_IMAGES
                KIND_LONG -> ++unpinnedLong <= MAX_UNPINNED_LONG
                KIND_FILE -> ++unpinnedFiles <= MAX_UNPINNED_FILES
                else -> ++unpinned <= MAX_UNPINNED
            }
            if (ok) kept.add(r) else drop.add(r)
        }
        val budget = ClipLimits.totalBytes(appContext)
        var total = kept.filter { it.kind != KIND_TEXT }.sumOf { it.bytes }
        if (total > budget) {
            val newest = kept.firstOrNull()?.key
            for (r in kept.asReversed()) {
                if (total <= budget) break
                if (r.pinned || r.kind == KIND_TEXT || r.key == newest) continue
                drop.add(r)
                total -= r.bytes
            }
        }
        if (drop.isEmpty()) return
        db.deleteKeys(drop.map { it.key })
        drop.forEach { deleteBacking(it.key) }
    }

    private fun deleteBacking(text: String) {
        when {
            text.isClipImage() -> images.delete(text.clipImageName())
            text.isClipLong() -> texts.delete(text.clipLongName())
            text.isClipFile() -> files.delete(text.clipFileName())
        }
    }

    private fun previewOf(text: String): String {
        val cut = text.take(PREVIEW_LENGTH)
        return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    companion object {
        private const val INLINE_MAX = 1500
        const val MAX_TEXT_LENGTH = 10_000_000
        private const val PREVIEW_LENGTH = 400
        private const val MAX_UNPINNED = 100
        private const val MAX_UNPINNED_LONG = 30
        private const val MAX_UNPINNED_IMAGES = 25
        private const val MAX_UNPINNED_FILES = 25
        private const val LIST_LIMIT = 400
        private const val ORPHAN_MIN_AGE_MS = 10L * 60L * 1000L
        private const val KIND_TEXT = 0
        private const val KIND_IMAGE = 1
        private const val KIND_LONG = 2
        private const val KIND_FILE = 3

        private val changes = MutableStateFlow(0L)
        private val migrated = AtomicBoolean(false)
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
    private val candidatesKey = stringPreferencesKey("learn_candidates")
    private val rankChoicesKey = stringPreferencesKey("rank_choices")
    private val rankProfilesKey = stringPreferencesKey("rank_profiles")

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
                words.add(0, LearnedEntry(clean(snapshot.word), "", saved.permille, saved.last, saved.uses))
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
            var uses = 0
            val base = if (old >= 0) {
                val prev = list.removeAt(old)
                if (prev.second == b) {
                    uses = prev.uses
                    ForgettingCurve.effective(prev, now)
                } else 0.0
            } else 0.0
            list.add(0, LearnedEntry(a, b, ForgettingCurve.reinforce(base, weight), now, uses + weight))
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

    suspend fun learnBatch(
        newWords: List<String>,
        newPairs: List<Pair<String, String>>,
        strongWords: List<String> = emptyList(),
        suspectWords: List<String> = emptyList()
    ): List<String> {
        if (newWords.isEmpty() && newPairs.isEmpty() && strongWords.isEmpty() && suspectWords.isEmpty()) return emptyList()
        if (!appContext.isUserStorageUnlocked()) return emptyList()
        val fresh = ArrayList<String>()
        store.edit { p ->
            val blockedKeys = (p[blockedKey]?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList())
                .mapTo(HashSet<String>()) { BigDictionary.key(it) }
            if (newWords.isNotEmpty() || strongWords.isNotEmpty() || suspectWords.isNotEmpty()) {
                val now = ForgettingCurve.nowMinutes()
                val list = decodeWords(p[key], now).toMutableList()
                val promoted = ArrayList<String>()
                if (suspectWords.isNotEmpty() || p[candidatesKey] != null) {
                    val candidates = decodeWords(p[candidatesKey], now).toMutableList()
                    for (raw in suspectWords) {
                        val w = clean(raw)
                        if (w.length < 3 || w.length > 30) continue
                        val ck = BigDictionary.key(w.lowercase())
                        val idx = candidates.indexOfFirst { BigDictionary.key(it.first.lowercase()) == ck }
                        if (idx >= 0) {
                            candidates.removeAt(idx)
                            promoted.add(w)
                        } else {
                            candidates.add(0, LearnedEntry(w, "", 0, now))
                        }
                    }
                    candidates.removeAll { now - it.last > CANDIDATE_TTL_MIN }
                    while (candidates.size > MAX_CANDIDATES) candidates.removeAt(candidates.size - 1)
                    p[candidatesKey] = encodeWords(candidates)
                }
                fun learn(raw: String, weight: Int, report: Boolean) {
                    val w = clean(raw)
                    if (w.length < 3 || w.length > 30) return
                    if (blockedKeys.isNotEmpty() && BigDictionary.key(w.lowercase()) in blockedKeys) return
                    val old = list.indexOfFirst { it.first == w }
                    var before = 0.0
                    var uses = 0
                    if (old >= 0) {
                        val prev = list.removeAt(old)
                        before = ForgettingCurve.effective(prev, now)
                        uses = prev.uses
                    }
                    val permille = ForgettingCurve.reinforce(before, weight)
                    list.add(0, LearnedEntry(w, "", permille, now, uses + weight))
                    if (report && before < ForgettingCurve.WORD_TRUST && permille / 1000.0 >= ForgettingCurve.WORD_TRUST) fresh.add(w)
                }
                for (w in newWords) learn(w, 1, false)
                for (w in strongWords) learn(w, 3, true)
                for (w in promoted) learn(w, 3, true)
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
                    var base = 0.0
                    var uses = 0
                    if (old >= 0) {
                        val prevEntry = list.removeAt(old)
                        base = ForgettingCurve.effective(prevEntry, now)
                        uses = prevEntry.uses
                    }
                    list.add(0, LearnedEntry(a, b, ForgettingCurve.reinforce(base, 1), now, uses + 1))
                }
                ForgettingCurve.prune(list, now, MAX_PAIRS)
                p[pairKey] = encodePairs(list)
            }
        }
        return fresh
    }

    suspend fun weakenWord(word: String) {
        if (!appContext.isUserStorageUnlocked()) return
        val w = clean(word)
        if (w.isEmpty()) return
        val k = BigDictionary.key(w)
        store.edit { p ->
            val now = ForgettingCurve.nowMinutes()
            val list = decodeWords(p[key], now).toMutableList()
            val idx = list.indexOfFirst { BigDictionary.key(it.first) == k }
            if (idx < 0) return@edit
            val entry = list.removeAt(idx)
            val value = ForgettingCurve.effective(entry, now) * 0.5
            if (value >= ForgettingCurve.FLOOR) {
                list.add(idx, LearnedEntry(entry.first, "", (value * 1000).roundToInt().coerceIn(0, 1000), now, maxOf(1, entry.uses / 2)))
            }
            p[key] = encodeWords(list)
        }
    }

    suspend fun removeWords(words: List<String>) {
        words.forEach { removeWord(it) }
    }

    val wordScores: Flow<Map<String, Double>> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[key] }
        .distinctUntilChanged()
        .map { raw ->
            val now = ForgettingCurve.nowMinutes()
            val out = HashMap<String, Double>()
            for (e in decodeWords(raw)) {
                val v = ForgettingCurve.effective(e, now)
                if (v < ForgettingCurve.FLOOR) continue
                val k = BigDictionary.key(e.first)
                val old = out[k]
                if (old == null || v > old) out[k] = v
            }
            out
        }

    class RankData internal constructor(val choices: Map<String, Double>, val profiles: Map<String, Map<String, Double>>)

    private class RankEntry(val a: String, val b: String, val score: Double, val last: Long)

    val rankData: Flow<RankData> = appContext.credentialPrefsFlow()
        .map { p: Preferences -> p[rankChoicesKey] to p[rankProfilesKey] }
        .distinctUntilChanged()
        .map { (choicesRaw, profilesRaw) ->
            val now = ForgettingCurve.nowMinutes()
            val choices = HashMap<String, Double>()
            for (e in decodeRank(choicesRaw)) {
                val v = rankEffective(e, now)
                if (abs(v) >= RANK_FLOOR) choices[e.a + "|" + e.b] = v
            }
            val profiles = HashMap<String, HashMap<String, Double>>()
            for (e in decodeRank(profilesRaw)) {
                val v = rankEffective(e, now)
                if (v >= RANK_FLOOR) profiles.getOrPut(e.a) { HashMap() }[e.b] = v
            }
            RankData(choices, profiles)
        }

    suspend fun applyRank(choiceDeltas: List<Triple<String, String, Double>>, profile: String, profileWords: List<String>) {
        if (choiceDeltas.isEmpty() && profileWords.isEmpty()) return
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            val now = ForgettingCurve.nowMinutes()
            if (choiceDeltas.isNotEmpty()) {
                val map = LinkedHashMap<String, RankEntry>()
                for (e in decodeRank(p[rankChoicesKey])) map[e.a + "|" + e.b] = e
                for ((prefixKey, wordKey, delta) in choiceDeltas) {
                    if (prefixKey.isEmpty() || wordKey.isEmpty()) continue
                    val k = "$prefixKey|$wordKey"
                    val current = map[k]
                    val base = if (current != null) rankEffective(current, now) else 0.0
                    map[k] = RankEntry(prefixKey, wordKey, (base + delta).coerceIn(-3.0, 4.0), now)
                }
                p[rankChoicesKey] = encodeRank(pruneRank(map.values.toMutableList(), now, MAX_RANK_CHOICES))
            }
            if (profileWords.isNotEmpty()) {
                val map = LinkedHashMap<String, RankEntry>()
                for (e in decodeRank(p[rankProfilesKey])) map[e.a + "|" + e.b] = e
                for (raw in profileWords) {
                    val wordKey = BigDictionary.key(raw)
                    if (wordKey.isEmpty()) continue
                    val k = "$profile|$wordKey"
                    val current = map[k]
                    val base = if (current != null) rankEffective(current, now) else 0.0
                    map[k] = RankEntry(profile, wordKey, (base + 1.0).coerceAtMost(6.0), now)
                }
                p[rankProfilesKey] = encodeRank(pruneRank(map.values.toMutableList(), now, MAX_RANK_PROFILE))
            }
        }
    }

    private fun rankEffective(e: RankEntry, now: Long): Double {
        val hours = (now - e.last).coerceAtLeast(0L) / 60.0
        return e.score * 0.5.pow(hours / RANK_HALF_LIFE_H)
    }

    private fun pruneRank(list: MutableList<RankEntry>, now: Long, max: Int): List<RankEntry> {
        list.removeAll { abs(rankEffective(it, now)) < RANK_FLOOR }
        if (list.size <= max) return list
        return list.sortedByDescending { abs(rankEffective(it, now)) }.take(max)
    }

    private fun decodeRank(raw: String?): List<RankEntry> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).mapNotNull { e ->
            val parts = e.split(FIELD)
            if (parts.size < 4) null else {
                val score = parts[2].toDoubleOrNull()
                val last = parts[3].toLongOrNull()
                if (score == null || last == null) null else RankEntry(parts[0], parts[1], score, last)
            }
        }

    private fun encodeRank(list: List<RankEntry>): String =
        list.joinToString(SEP) { it.a + FIELD + it.b + FIELD + it.score + FIELD + it.last }

    /** يمسح الكلمات المتعلَّمة وأزواج التوقّع معًا */
    suspend fun clear() {
        if (!appContext.isUserStorageUnlocked()) return
        store.edit { p ->
            p.remove(key)
            p.remove(pairKey)
            p.remove(fixesKey)
            p.remove(candidatesKey)
            p.remove(rankChoicesKey)
            p.remove(rankProfilesKey)
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
                val permille = parts[1].toIntOrNull() ?: 0
                val uses = parts.getOrNull(3)?.toIntOrNull() ?: ForgettingCurve.estimateUses(permille)
                LearnedEntry(parts[0], "", permille, parts[2].toLongOrNull() ?: now, uses)
            } else {
                LearnedEntry(parts[0], "", ForgettingCurve.fromLegacyCount(parts.getOrNull(1)?.toIntOrNull() ?: 1), now)
            }
        }

    private fun decodePairEntries(raw: String?, now: Long): List<LearnedEntry> =
        if (raw.isNullOrEmpty()) emptyList() else raw.split(SEP).mapNotNull { e ->
            val parts = e.split(FIELD)
            when {
                parts.size >= 4 -> {
                    val permille = parts[2].toIntOrNull() ?: 0
                    val uses = parts.getOrNull(4)?.toIntOrNull() ?: ForgettingCurve.estimateUses(permille)
                    LearnedEntry(parts[0], parts[1], permille, parts[3].toLongOrNull() ?: now, uses)
                }
                parts.size == 3 -> LearnedEntry(parts[0], parts[1], ForgettingCurve.fromLegacyCount(parts[2].toIntOrNull() ?: 1), now)
                else -> null
            }
        }

    private fun decodeRawPairs(raw: String?, now: Long = ForgettingCurve.nowMinutes()): List<LearnedEntry> =
        decodePairEntries(raw, now)

    private fun encodeWords(list: List<LearnedEntry>): String =
        list.joinToString(SEP) { it.first + FIELD + it.permille + FIELD + it.last + FIELD + it.uses }

    private fun encodePairs(list: List<LearnedEntry>): String =
        list.joinToString(SEP) { it.first + FIELD + it.second + FIELD + it.permille + FIELD + it.last + FIELD + it.uses }

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
        const val MAX_CANDIDATES = 300
        const val CANDIDATE_TTL_MIN = 14L * 24L * 60L
        const val MAX_RANK_CHOICES = 1500
        const val MAX_RANK_PROFILE = 2400
        const val RANK_HALF_LIFE_H = 720.0
        const val RANK_FLOOR = 0.05
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
