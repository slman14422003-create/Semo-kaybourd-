package com.semo.keyboard.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

enum class VoskFailure { NETWORK, SPACE, CORRUPT, MEMORY, UNKNOWN }

sealed interface VoskState {
    object Missing : VoskState
    data class Downloading(val percent: Int) : VoskState
    data class Extracting(val percent: Int) : VoskState
    object Ready : VoskState
    data class Failed(val reason: VoskFailure) : VoskState
}

enum class VoskTier { STANDARD, ACCURATE }

class VoskModelSpec(val code: String, val tier: VoskTier, val id: String, val bytes: Long) {
    val url: String get() = "https://alphacephei.com/vosk/models/vosk-model-$id.zip"
    val megabytes: Int get() = ((bytes + 500_000L) / 1_000_000L).toInt()
    val sizeLabel: String get() = if (bytes >= 1_000_000_000L) "%.1f GB".format(bytes / 1_000_000_000.0) else "$megabytes MB"
}

object VoskModels {
    private const val HEAVY_BYTES = 700_000_000L
    private const val MIN_RAM_BYTES = 5_000_000_000L

    private fun std(code: String, id: String, bytes: Long) = VoskModelSpec(code, VoskTier.STANDARD, id, bytes)
    private fun acc(code: String, id: String, bytes: Long) = VoskModelSpec(code, VoskTier.ACCURATE, id, bytes)

    val standard: Map<String, VoskModelSpec> = listOf(
        std("ar", "small-ar-0.3", 104_351_896L),
        std("en", "small-en-us-0.15", 41_205_931L),
        std("fr", "small-fr-0.22", 42_233_323L),
        std("es", "small-es-0.42", 39_817_833L),
        std("de", "small-de-0.15", 46_499_967L),
        std("tr", "small-tr-0.3", 36_855_784L),
        std("ru", "small-ru-0.22", 46_236_750L),
        std("fa", "small-fa-0.42", 53_431_220L),
        std("pt", "small-pt-0.3", 32_453_112L),
        std("it", "small-it-0.22", 49_665_141L),
        std("nl", "small-nl-0.22", 40_441_176L),
        std("pl", "small-pl-0.22", 52_979_372L),
        std("uk", "small-uk-v3-small", 143_914_407L)
    ).associateBy { it.code }

    val accurate: Map<String, VoskModelSpec> = listOf(
        acc("ar", "ar-0.22-linto-1.1.0", 1_377_395_170L),
        acc("en", "en-us-0.22-lgraph", 130_557_655L),
        acc("fr", "fr-0.22", 1_523_026_348L),
        acc("es", "es-0.42", 1_484_681_703L),
        acc("de", "de-0.21", 2_031_717_803L),
        acc("ru", "ru-0.42", 1_937_602_113L),
        acc("fa", "fa-0.42", 1_667_089_770L),
        acc("pt", "pt-fb-v0.1.1-pruned", 1_223_922_169L),
        acc("it", "it-0.22", 1_306_765_292L),
        acc("nl", "nl-spraakherkenning-0.6-lgraph", 105_951_663L),
        acc("uk", "uk-v3-lgraph", 340_762_018L)
    ).associateBy { it.code }

    /** كل أكواد اللغات التي لها نموذج (قياسي على الأقل) */
    val all: Map<String, VoskModelSpec> get() = standard

    fun specFor(code: String): VoskModelSpec? = standard[code]

    fun spec(code: String, tier: VoskTier): VoskModelSpec? =
        if (tier == VoskTier.STANDARD) standard[code] else accurate[code]

    fun byId(code: String, id: String): VoskModelSpec? =
        listOfNotNull(standard[code], accurate[code]).firstOrNull { it.id == id }

    /** النماذج الضخمة (فوق 700 ميغابايت) تحتاج ذاكرة جهاز كبيرة وإلا تُغلق اللوحة عند التحميل */
    fun needsBigRam(spec: VoskModelSpec): Boolean = spec.bytes >= HEAVY_BYTES

    fun deviceRamOk(context: android.content.Context): Boolean {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return true
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem >= MIN_RAM_BYTES
    }
}

object VoskModelManager {

    private class Handled : Exception()

    private const val ROOT = "vosk"
    private const val READY_MARK = ".ready"
    private const val IDLE_RELEASE_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val flow = MutableStateFlow<Map<String, VoskState>>(emptyMap())
    private val loadLock = Any()
    private val jobs = HashMap<String, Job>()
    private var model: Model? = null
    private var modelCode: String? = null

    @Volatile
    private var busy = false
    private var logConfigured = false

    val states: StateFlow<Map<String, VoskState>> get() = flow

    fun stateOf(map: Map<String, VoskState>, code: String): VoskState = map[code] ?: VoskState.Missing

    private fun setState(code: String, value: VoskState) {
        flow.value = flow.value + (code to value)
    }

    private fun downloading(code: String): Boolean = synchronized(jobs) { jobs[code]?.isActive == true }

    private val releaseTask = Runnable { release() }

    private fun root(context: Context) = File(context.applicationContext.filesDir, ROOT)
    private fun modelDir(context: Context, code: String) = File(root(context), code)
    private fun partFile(context: Context, code: String) = File(root(context), "$code.zip.part")
    private fun tempDir(context: Context, code: String) = File(root(context), "$code.tmp")

    fun isReady(context: Context, code: String): Boolean = File(modelDir(context, code), READY_MARK).exists()

    /** معرّف النموذج المنزَّل (العلامة القديمة "1" = النموذج القديم: العربي mgb2 وغيره هو القياسي الحالي) */
    fun installedId(context: Context, code: String): String? {
        val mark = File(modelDir(context, code), READY_MARK)
        if (!mark.exists()) return null
        val text = runCatching { mark.readText().trim() }.getOrDefault("")
        if (text.isNotEmpty() && text != "1") return text
        return if (code == "ar") "ar-mgb2-0.4" else VoskModels.standard[code]?.id
    }

    fun refresh(context: Context) {
        cleanLeftovers(context)
        val next = HashMap(flow.value)
        for (code in VoskModels.all.keys) {
            if (downloading(code)) continue
            next[code] = if (isReady(context, code)) VoskState.Ready else VoskState.Missing
        }
        flow.value = next
    }

    fun cleanLeftovers(context: Context) {
        val entries = root(context).listFiles() ?: return
        for (entry in entries) {
            val name = entry.name
            if (!name.contains('.')) continue
            val code = name.substringBefore('.')
            if (downloading(code)) continue
            runCatching { entry.deleteRecursively() }
        }
    }

    fun leftoverBytes(context: Context): Long {
        val entries = root(context).listFiles() ?: return 0L
        var total = 0L
        for (entry in entries) {
            val name = entry.name
            if (!name.contains('.') || downloading(name.substringBefore('.'))) continue
            total += if (entry.isFile) entry.length() else entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        return total
    }

    private fun purgeStale(context: Context, code: String) {
        runCatching { partFile(context, code).delete() }
        runCatching { File(root(context), "$code.zip").delete() }
        runCatching { tempDir(context, code).deleteRecursively() }
    }

    fun installedBytes(context: Context, code: String): Long {
        val dir = modelDir(context, code)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun totalInstalledBytes(context: Context): Long = VoskModels.all.keys.sumOf { installedBytes(context, it) }

    fun download(context: Context, code: String, tier: VoskTier = VoskTier.STANDARD) {
        val spec = VoskModels.spec(code, tier) ?: return
        if (downloading(code)) return
        if (VoskModels.needsBigRam(spec) && !VoskModels.deviceRamOk(context)) {
            setState(code, VoskState.Failed(VoskFailure.MEMORY))
            return
        }
        val app = context.applicationContext
        setState(code, VoskState.Downloading(0))
        val started = scope.launch {
            try {
                runDownload(app, spec)
            } catch (e: Handled) {
                Unit
            } catch (e: CancellationException) {
                setState(code, if (isReady(app, code)) VoskState.Ready else VoskState.Missing)
                throw e
            } catch (e: Throwable) {
                setState(code, VoskState.Failed(if (e is IOException) VoskFailure.NETWORK else VoskFailure.UNKNOWN))
            }
        }
        synchronized(jobs) { jobs[code] = started }
    }

    fun cancel(code: String) {
        synchronized(jobs) { jobs[code] }?.cancel()
    }

    fun delete(context: Context, code: String) {
        cancel(code)
        if (modelCode == code) release()
        modelDir(context, code).deleteRecursively()
        purgeStale(context, code)
        setState(code, VoskState.Missing)
    }

    fun loadModel(context: Context, code: String): Model? = synchronized(loadLock) {
        main.removeCallbacks(releaseTask)
        busy = true
        if (modelCode == code) model?.let { return it }
        if (modelCode != null) {
            model?.let { runCatching { it.close() } }
            model = null
            modelCode = null
        }
        if (!isReady(context, code)) {
            busy = false
            return null
        }
        if (!logConfigured) {
            logConfigured = true
            runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
        }
        val loaded = runCatching { Model(modelDir(context, code).absolutePath) }.getOrNull()
        model = loaded
        modelCode = if (loaded != null) code else null
        if (loaded == null) busy = false
        loaded
    }

    fun trim() {
        if (!busy) release()
    }

    fun idle() {
        busy = false
        main.removeCallbacks(releaseTask)
        main.postDelayed(releaseTask, IDLE_RELEASE_MS)
    }

    fun release() {
        synchronized(loadLock) {
            main.removeCallbacks(releaseTask)
            val current = model
            model = null
            modelCode = null
            if (current != null) runCatching { current.close() }
        }
    }

    private suspend fun runDownload(context: Context, spec: VoskModelSpec) {
        val dir = root(context)
        dir.mkdirs()
        purgeStale(context, spec.code)
        if (dir.usableSpace < spec.bytes * 3L / 2L) {
            setState(spec.code, VoskState.Failed(VoskFailure.SPACE))
            return
        }
        streamInstall(context, spec)
        setState(spec.code, VoskState.Ready)
    }

    private class Counting(input: InputStream, val onBytes: (Long) -> Unit) : FilterInputStream(input) {
        var total = 0L
            private set
        var broken = false
            private set

        override fun read(): Int {
            val b = try {
                super.read()
            } catch (e: IOException) {
                broken = true
                throw e
            }
            if (b >= 0) tick(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = try {
                super.read(b, off, len)
            } catch (e: IOException) {
                broken = true
                throw e
            }
            if (n > 0) tick(n.toLong())
            return n
        }

        private fun tick(n: Long) {
            total += n
            onBytes(total)
        }
    }

    private fun isNoSpace(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            val m = cur.message.orEmpty()
            if (m.contains("ENOSPC") || m.contains("No space left", ignoreCase = true)) return true
            cur = cur.cause
        }
        return false
    }

    private suspend fun streamInstall(context: Context, spec: VoskModelSpec) {
        val code = spec.code
        val temp = tempDir(context, code)
        temp.deleteRecursively()
        temp.mkdirs()
        val guard = temp.canonicalPath + File.separator
        var counting: Counting? = null
        var expected = spec.bytes
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SemoKeyboard")
        }
        try {
            val status = connection.responseCode
            if (status != 200) throw IOException("http $status")
            expected = connection.contentLengthLong.takeIf { it > 0L } ?: spec.bytes
            var lastPercent = -1
            setState(code, VoskState.Downloading(0))
            val source = Counting(connection.inputStream) { bytes ->
                val percent = (bytes * 100 / expected).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    setState(code, VoskState.Downloading(percent))
                }
            }
            counting = source
            ZipInputStream(BufferedInputStream(source, 64 * 1024)).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val target = File(temp, entry.name)
                    if (!target.canonicalPath.startsWith(guard)) throw IOException("path")
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            kotlin.coroutines.coroutineContext.ensureActive()
                        }
                    }
                }
                while (source.read(buffer) >= 0) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                }
            }
            if (source.total < expected) throw IOException("incomplete")
        } catch (e: CancellationException) {
            temp.deleteRecursively()
            throw e
        } catch (e: Throwable) {
            temp.deleteRecursively()
            val reason = when {
                isNoSpace(e) -> VoskFailure.SPACE
                counting == null || counting.broken || counting.total < expected -> VoskFailure.NETWORK
                else -> VoskFailure.CORRUPT
            }
            setState(code, VoskState.Failed(reason))
            throw Handled()
        } finally {
            connection.disconnect()
        }
        val found = temp.walkTopDown().firstOrNull { File(it, "am/final.mdl").exists() || File(it, "conf/model.conf").exists() }
        if (found == null) {
            temp.deleteRecursively()
            setState(code, VoskState.Failed(VoskFailure.CORRUPT))
            throw Handled()
        }
        val target = modelDir(context, code)
        if (modelCode == code) release()
        target.deleteRecursively()
        val moved = found.renameTo(target)
        if (!moved) {
            found.copyRecursively(target, overwrite = true)
        }
        temp.deleteRecursively()
        File(target, READY_MARK).writeText(spec.id)
    }
}
