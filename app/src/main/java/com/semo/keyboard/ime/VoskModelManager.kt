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

enum class VoskFailure { NETWORK, SPACE, CORRUPT, UNKNOWN }

sealed interface VoskState {
    object Missing : VoskState
    data class Downloading(val percent: Int) : VoskState
    data class Extracting(val percent: Int) : VoskState
    object Ready : VoskState
    data class Failed(val reason: VoskFailure) : VoskState
}

class VoskModelSpec(val code: String, val url: String, val bytes: Long) {
    val megabytes: Int get() = ((bytes + 500_000L) / 1_000_000L).toInt()
}

object VoskModels {
    private const val BASE = "https://alphacephei.com/vosk/models/"

    val all: Map<String, VoskModelSpec> = listOf(
        VoskModelSpec("ar", BASE + "vosk-model-ar-mgb2-0.4.zip", 333_241_610L),
        VoskModelSpec("en", BASE + "vosk-model-small-en-us-0.15.zip", 41_205_931L),
        VoskModelSpec("fr", BASE + "vosk-model-small-fr-0.22.zip", 42_233_323L),
        VoskModelSpec("es", BASE + "vosk-model-small-es-0.42.zip", 39_817_833L),
        VoskModelSpec("de", BASE + "vosk-model-small-de-0.15.zip", 46_499_967L),
        VoskModelSpec("tr", BASE + "vosk-model-small-tr-0.3.zip", 36_855_784L),
        VoskModelSpec("ru", BASE + "vosk-model-small-ru-0.22.zip", 46_236_750L),
        VoskModelSpec("fa", BASE + "vosk-model-small-fa-0.42.zip", 53_431_220L),
        VoskModelSpec("pt", BASE + "vosk-model-small-pt-0.3.zip", 32_453_112L),
        VoskModelSpec("it", BASE + "vosk-model-small-it-0.22.zip", 49_665_141L),
        VoskModelSpec("nl", BASE + "vosk-model-small-nl-0.22.zip", 40_441_176L),
        VoskModelSpec("pl", BASE + "vosk-model-small-pl-0.22.zip", 52_979_372L),
        VoskModelSpec("uk", BASE + "vosk-model-small-uk-v3-small.zip", 143_914_407L)
    ).associateBy { it.code }

    fun specFor(code: String): VoskModelSpec? = all[code]
}

object VoskModelManager {

    private class Handled : Exception()

    private const val ROOT = "vosk"
    private const val READY_MARK = ".ready"
    private const val IDLE_RELEASE_MS = 20_000L
    private const val SPACE_FACTOR = 2L

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

    fun download(context: Context, code: String) {
        val spec = VoskModels.specFor(code) ?: return
        if (downloading(code)) return
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
        if (dir.usableSpace < spec.bytes * SPACE_FACTOR) {
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
        File(target, READY_MARK).writeText("1")
    }
}
