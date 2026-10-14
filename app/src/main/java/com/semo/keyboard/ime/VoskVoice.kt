package com.semo.keyboard.ime

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.semo.keyboard.domain.model.VoiceStatus
import kotlin.math.log10
import kotlin.math.sqrt
import org.json.JSONObject
import org.vosk.Recognizer

class VoskVoice(
    private val context: Context,
    private val callback: VoiceInput.Callback
) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var loop: Loop? = null

    @Volatile
    var active: Boolean = false
        private set

    fun start(code: String) {
        stopLoop(cancelled = true)
        val gen = ++generation
        active = true
        callback.onStatus(VoiceStatus.STARTING)
        Thread({
            val model = VoskModelManager.loadModel(context, code)
            main.post {
                if (gen != generation || !active) {
                    if (model != null) VoskModelManager.idle()
                    return@post
                }
                if (model == null) {
                    active = false
                    VoskModelManager.idle()
                    callback.onStatus(VoiceStatus.FAILED)
                    return@post
                }
                val recognizer = runCatching { Recognizer(model, SAMPLE_RATE.toFloat()) }.getOrNull()
                if (recognizer == null) {
                    active = false
                    VoskModelManager.idle()
                    callback.onStatus(VoiceStatus.FAILED)
                    return@post
                }
                val worker = Loop(recognizer, gen)
                loop = worker
                worker.start()
            }
        }, "vosk-load").start()
    }

    fun finish() {
        val worker = loop
        if (worker == null) {
            if (active) {
                active = false
                generation++
                callback.onStatus(VoiceStatus.PAUSED)
            }
            return
        }
        worker.stopRequested = true
    }

    fun cancel() {
        stopLoop(cancelled = true)
        generation++
        active = false
    }

    private fun stopLoop(cancelled: Boolean) {
        val worker = loop ?: return
        loop = null
        worker.cancelled = cancelled
        worker.stopRequested = true
    }

    private fun text(json: String, key: String): String =
        runCatching { JSONObject(json).optString(key, "").trim() }.getOrDefault("")

    private inner class Loop(
        private val recognizer: Recognizer,
        private val gen: Int
    ) : Thread("vosk-listen") {

        @Volatile
        var stopRequested = false

        @Volatile
        var cancelled = false

        private fun alive(): Boolean = gen == generation && !cancelled

        private fun post(block: () -> Unit) {
            main.post { if (alive()) block() }
        }

        override fun run() {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBuffer, SAMPLE_RATE / 2 * 2) * 2
            val audio = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
            }.getOrNull()
            var failed = audio == null || audio.state != AudioRecord.STATE_INITIALIZED
            if (!failed) {
                failed = runCatching { audio!!.startRecording() }.isFailure
            }
            if (failed) {
                runCatching { audio?.release() }
                runCatching { recognizer.close() }
                main.post {
                    if (gen == generation) {
                        active = false
                        loop = null
                        VoskModelManager.idle()
                        callback.onStatus(VoiceStatus.FAILED)
                    }
                }
                return
            }
            post { callback.onStatus(VoiceStatus.LISTENING) }
            val buffer = ShortArray(SAMPLE_RATE / 5)
            var lastPartial = ""
            var levelValue = 0f
            var crashed = false
            try {
                while (!stopRequested && !cancelled) {
                    val read = audio!!.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        crashed = true
                        break
                    }
                    if (read == 0) continue
                    var sum = 0.0
                    for (i in 0 until read) {
                        val v = buffer[i].toDouble()
                        sum += v * v
                    }
                    val rms = sqrt(sum / read)
                    val db = if (rms > 1.0) 20.0 * log10(rms / 32768.0) else -90.0
                    val target = ((db + 55.0) / 40.0).coerceIn(0.0, 1.0).toFloat()
                    levelValue = levelValue * 0.55f + target * 0.45f
                    val shown = levelValue
                    post { callback.onLevel(shown) }
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val result = text(recognizer.result, "text")
                        lastPartial = ""
                        if (result.isNotEmpty()) post { callback.onFinal(result) }
                    } else {
                        val partial = text(recognizer.partialResult, "partial")
                        if (partial.isNotEmpty() && partial != lastPartial) {
                            lastPartial = partial
                            post { callback.onPartial(partial) }
                        }
                    }
                }
                if (!cancelled) {
                    val tail = text(recognizer.finalResult, "text")
                    if (tail.isNotEmpty()) post { callback.onFinal(tail) }
                }
            } catch (t: Throwable) {
                crashed = true
            } finally {
                runCatching { audio?.stop() }
                runCatching { audio?.release() }
                runCatching { recognizer.close() }
            }
            main.post {
                if (gen == generation) {
                    active = false
                    loop = null
                    VoskModelManager.idle()
                    if (!cancelled) callback.onStatus(if (crashed) VoiceStatus.FAILED else VoiceStatus.PAUSED)
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000
    }
}
