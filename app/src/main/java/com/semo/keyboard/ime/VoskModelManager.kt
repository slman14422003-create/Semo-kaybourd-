package com.semo.keyboard.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
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

object VoskModelManager {

    private class Handled : Exception()

    const val ARABIC_URL = "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip"
    const val ARABIC_ZIP_BYTES = 333_241_610L
    const val ARABIC_LABEL = "vosk-model-ar-mgb2-0.4"

    private const val ROOT = "vosk"
    private const val MODEL_DIR = "ar"
    private const val PART_FILE = "ar.zip.part"
    private const val READY_MARK = ".ready"
    private const val IDLE_RELEASE_MS = 20_000L
    private const val MIN_ZIP_BYTES = 300_000_000L
    private const val SPACE_FACTOR = 3L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val flow = MutableStateFlow<VoskState>(VoskState.Missing)
    private val loadLock = Any()
    private var job: Job? = null
    private var model: Model? = null

    @Volatile
    private var busy = false
    private var logConfigured = false

    val state: StateFlow<VoskState> get() = flow

    private val releaseTask = Runnable { release() }

    private fun root(context: Context) = File(context.applicationContext.filesDir, ROOT)
    private fun modelDir(context: Context) = File(root(context), MODEL_DIR)

    fun isReady(context: Context): Boolean = File(modelDir(context), READY_MARK).exists()

    fun refresh(context: Context) {
        if (job?.isActive == true) return
        flow.value = if (isReady(context)) VoskState.Ready else VoskState.Missing
    }

    fun download(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        flow.value = VoskState.Downloading(0)
        job = scope.launch {
            try {
                runDownload(app)
            } catch (e: Handled) {
                Unit
            } catch (e: CancellationException) {
                flow.value = if (isReady(app)) VoskState.Ready else VoskState.Missing
                throw e
            } catch (e: Throwable) {
                flow.value = VoskState.Failed(if (e is IOException) VoskFailure.NETWORK else VoskFailure.UNKNOWN)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun delete(context: Context) {
        job?.cancel()
        release()
        root(context).deleteRecursively()
        flow.value = VoskState.Missing
    }

    fun loadModel(context: Context): Model? = synchronized(loadLock) {
        main.removeCallbacks(releaseTask)
        busy = true
        model?.let { return it }
        if (!isReady(context)) {
            busy = false
            return null
        }
        if (!logConfigured) {
            logConfigured = true
            runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
        }
        val loaded = runCatching { Model(modelDir(context).absolutePath) }.getOrNull()
        model = loaded
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
            if (current != null) runCatching { current.close() }
        }
    }

    private suspend fun runDownload(context: Context) {
        val dir = root(context)
        dir.mkdirs()
        val part = File(dir, PART_FILE)
        val free = dir.usableSpace
        val needed = ARABIC_ZIP_BYTES * SPACE_FACTOR - part.length()
        if (free < needed) {
            flow.value = VoskState.Failed(VoskFailure.SPACE)
            return
        }
        downloadZip(part)
        extract(part, dir)
        part.delete()
        flow.value = VoskState.Ready
    }

    private suspend fun downloadZip(part: File) {
        var have = if (part.exists()) part.length() else 0L
        val connection = (URL(ARABIC_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SemoKeyboard")
            if (have > 0L) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = connection.responseCode
            if (code == 416) {
                part.delete()
                throw IOException("range")
            }
            if (code != 200 && code != 206) throw IOException("http $code")
            val resumed = code == 206 && have > 0L
            if (!resumed) have = 0L
            val total = have + connection.contentLengthLong.coerceAtLeast(0L)
            val expected = if (total > have) total else ARABIC_ZIP_BYTES
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = have
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val percent = (written * 100 / expected).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            flow.value = VoskState.Downloading(percent)
                        }
                    }
                }
            }
            if (part.length() < MIN_ZIP_BYTES || part.length() < expected) {
                throw IOException("incomplete")
            }
        } finally {
            connection.disconnect()
        }
    }

    private class Counting(input: InputStream, val onBytes: (Long) -> Unit) : FilterInputStream(input) {
        private var total = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) tick(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) tick(n.toLong())
            return n
        }

        private fun tick(n: Long) {
            total += n
            onBytes(total)
        }
    }

    private suspend fun extract(part: File, dir: File) {
        val temp = File(dir, "ar.tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        val size = part.length().coerceAtLeast(1L)
        var lastPercent = -1
        flow.value = VoskState.Extracting(0)
        val guard = temp.canonicalPath + File.separator
        try {
            ZipInputStream(
                BufferedInputStream(
                    Counting(FileInputStream(part)) { bytes ->
                        val percent = (bytes * 100 / size).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            flow.value = VoskState.Extracting(percent)
                        }
                    },
                    64 * 1024
                )
            ).use { zip ->
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
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            temp.deleteRecursively()
            throw e
        } catch (e: Throwable) {
            temp.deleteRecursively()
            part.delete()
            flow.value = VoskState.Failed(VoskFailure.CORRUPT)
            throw Handled()
        }
        val found = temp.walkTopDown().firstOrNull { File(it, "am/final.mdl").exists() }
        if (found == null) {
            temp.deleteRecursively()
            part.delete()
            flow.value = VoskState.Failed(VoskFailure.CORRUPT)
            throw Handled()
        }
        val target = File(dir, MODEL_DIR)
        release()
        target.deleteRecursively()
        val moved = found.renameTo(target)
        if (!moved) {
            found.copyRecursively(target, overwrite = true)
        }
        temp.deleteRecursively()
        File(target, READY_MARK).writeText("1")
    }
}
