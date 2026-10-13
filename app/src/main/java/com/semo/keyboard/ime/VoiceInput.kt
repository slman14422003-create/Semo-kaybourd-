package com.semo.keyboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.VoiceDialect
import com.semo.keyboard.domain.model.VoiceOptions
import com.semo.keyboard.domain.model.VoiceStatus
import java.util.Locale

object VoiceLanguages {
    private val arabCountries = setOf(
        "SA", "JO", "SY", "LB", "PS", "IQ", "EG", "AE", "KW", "QA", "BH", "OM", "YE", "LY", "TN", "DZ", "MA", "SD"
    )

    private val defaults = mapOf(
        "en" to "en-US", "ar" to "ar-SA", "fr" to "fr-FR", "es" to "es-ES", "de" to "de-DE",
        "tr" to "tr-TR", "ru" to "ru-RU", "fa" to "fa-IR", "pt" to "pt-BR", "it" to "it-IT",
        "id" to "id-ID", "nl" to "nl-NL", "pl" to "pl-PL", "uk" to "uk-UA"
    )

    fun tagFor(
        language: KeyboardLanguage,
        device: Locale = Locale.getDefault(),
        dialect: VoiceDialect = VoiceDialect.AUTO
    ): String {
        val code = language.code
        if (code == "ar") {
            dialect.tag?.let { return it }
            val country = device.country.uppercase(Locale.ROOT)
            return if (country in arabCountries) "ar-$country" else "ar-SA"
        }
        if (device.language == code && device.country.isNotEmpty()) return "$code-${device.country}"
        return defaults[code] ?: code
    }
}

class VoiceInput(
    private val context: Context,
    private val callback: Callback
) {
    interface Callback {
        fun onStatus(status: VoiceStatus)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onLevel(level: Float)
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tag = "en-US"
    private var running = false
    private var stopping = false
    private var inFlight = false
    private var silentRounds = 0
    private var busyRetries = 0
    private var preferOffline = false
    private var forceOnline = false
    private var usingOnDevice = false
    private var options = VoiceOptions()
    private var triedOffline = false
    private var triedBaseTag = false
    private var level = 0f
    private var levelAt = 0L

    private val restartTask = Runnable { if (running && !stopping) listen() }
    private val stopTask = Runnable { if (stopping) finishStop() }

    val active: Boolean get() = running

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(languageTag: String, voiceOptions: VoiceOptions = VoiceOptions()) {
        cancel()
        tag = languageTag
        options = voiceOptions
        silentRounds = 0
        busyRetries = 0
        preferOffline = false
        forceOnline = false
        triedOffline = false
        triedBaseTag = false
        if (!hasPermission()) {
            callback.onStatus(VoiceStatus.NO_PERMISSION)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onStatus(VoiceStatus.UNAVAILABLE)
            return
        }
        running = true
        callback.onStatus(VoiceStatus.STARTING)
        listen()
    }

    fun finish() {
        if (!running) {
            callback.onStatus(VoiceStatus.PAUSED)
            return
        }
        stopping = true
        main.removeCallbacks(restartTask)
        if (!inFlight) {
            finishStop()
            return
        }
        runCatching { recognizer?.stopListening() }
        main.postDelayed(stopTask, 2500)
    }

    fun cancel() {
        running = false
        stopping = false
        main.removeCallbacks(restartTask)
        main.removeCallbacks(stopTask)
        destroyRecognizer()
    }

    private fun finishStop() {
        running = false
        stopping = false
        main.removeCallbacks(restartTask)
        main.removeCallbacks(stopTask)
        destroyRecognizer()
        callback.onStatus(VoiceStatus.PAUSED)
    }

    private fun fail(status: VoiceStatus) {
        running = false
        stopping = false
        main.removeCallbacks(restartTask)
        main.removeCallbacks(stopTask)
        destroyRecognizer()
        callback.onStatus(status)
    }

    private fun destroyRecognizer() {
        val r = recognizer
        recognizer = null
        inFlight = false
        if (r != null) runCatching {
            r.setRecognitionListener(null)
            r.cancel()
            r.destroy()
        }
    }

    private fun create(): SpeechRecognizer? {
        val wantOnDevice = (preferOffline || (options.offlineFirst && !forceOnline)) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val r = runCatching {
            if (wantOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: return null
        usingOnDevice = wantOnDevice
        r.setRecognitionListener(listener)
        return r
    }

    private fun listen() {
        if (!running) return
        val r = recognizer ?: create()?.also { recognizer = it }
        if (r == null) {
            fail(VoiceStatus.UNAVAILABLE)
            return
        }
        inFlight = true
        val started = runCatching { r.startListening(buildIntent()) }.isSuccess
        if (!started) {
            inFlight = false
            fail(VoiceStatus.FAILED)
        }
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2200L)
        putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1800L)
        putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 1200L)
        putExtra("android.speech.extra.ENABLE_FORMATTING", "quality")
        putExtra("android.speech.extra.MASK_OFFENSIVE_WORDS", false)
        if (options.bias.isNotEmpty()) {
            putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(options.bias))
        }
    }

    private fun best(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return null
        if (list.isEmpty()) return null
        val raw = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val scores = raw?.takeIf { it.size == list.size && it.any { v -> v > 0f } }
        val rank = options.rank
        var index = -1
        var top = Double.NEGATIVE_INFINITY
        for (i in list.indices) {
            val text = list[i].trim()
            if (text.isEmpty()) continue
            val confidence = scores?.get(i)?.toDouble() ?: (1.0 / (1.0 + i * 0.35))
            val known = if (rank != null) runCatching { rank(text) }.getOrDefault(0.0) else 0.0
            val value = confidence + known * RANK_WEIGHT
            if (value > top) {
                top = value
                index = i
            }
        }
        return if (index >= 0) list[index].trim() else null
    }

    private fun scheduleRestart(delayMs: Long = 150L) {
        main.removeCallbacks(restartTask)
        main.postDelayed(restartTask, delayMs)
    }

    private fun handleResults(results: Bundle?) {
        inFlight = false
        val text = best(results)
        if (text != null) {
            silentRounds = 0
            callback.onFinal(text)
        }
        if (stopping || !running) {
            finishStop()
            return
        }
        if (text == null) silentRounds++
        if (silentRounds >= MAX_SILENT_ROUNDS) finishStop() else scheduleRestart()
    }

    private fun handleError(error: Int) {
        inFlight = false
        if (!running) return
        if (stopping) {
            finishStop()
            return
        }
        when (error) {
            ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH -> {
                silentRounds++
                if (silentRounds >= MAX_SILENT_ROUNDS) finishStop() else scheduleRestart()
            }
            ERROR_CLIENT, ERROR_RECOGNIZER_BUSY -> {
                if (busyRetries++ < 3) {
                    destroyRecognizer()
                    scheduleRestart(350L)
                } else fail(VoiceStatus.FAILED)
            }
            ERROR_INSUFFICIENT_PERMISSIONS -> fail(VoiceStatus.NO_PERMISSION)
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                if (usingOnDevice && !forceOnline) {
                    forceOnline = true
                    preferOffline = false
                    destroyRecognizer()
                    scheduleRestart(100L)
                } else if (!triedBaseTag && tag.contains('-')) {
                    triedBaseTag = true
                    tag = tag.substringBefore('-')
                    preferOffline = false
                    destroyRecognizer()
                    scheduleRestart(100L)
                } else retryOfflineOr(VoiceStatus.UNAVAILABLE)
            }
            ERROR_NETWORK_TIMEOUT, ERROR_NETWORK, ERROR_SERVER, ERROR_TOO_MANY_REQUESTS, ERROR_SERVER_DISCONNECTED ->
                retryOfflineOr(VoiceStatus.NETWORK)
            else -> fail(VoiceStatus.FAILED)
        }
    }

    private fun retryOfflineOr(status: VoiceStatus) {
        if (!triedOffline) {
            triedOffline = true
            forceOnline = false
            preferOffline = true
            destroyRecognizer()
            scheduleRestart(100L)
        } else fail(status)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (running) callback.onStatus(VoiceStatus.LISTENING)
        }

        override fun onBeginningOfSpeech() {
            silentRounds = 0
            busyRetries = 0
        }

        override fun onRmsChanged(rmsdB: Float) {
            val now = SystemClock.uptimeMillis()
            if (now - levelAt < 60L) return
            levelAt = now
            val target = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            level = level * 0.55f + target * 0.45f
            callback.onLevel(level)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = handleError(error)

        override fun onResults(results: Bundle?) = handleResults(results)

        override fun onPartialResults(partialResults: Bundle?) {
            if (!running) return
            best(partialResults)?.let { callback.onPartial(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val MAX_SILENT_ROUNDS = 2
        const val RANK_WEIGHT = 0.6
        const val ERROR_NETWORK_TIMEOUT = 1
        const val ERROR_NETWORK = 2
        const val ERROR_SERVER = 4
        const val ERROR_CLIENT = 5
        const val ERROR_SPEECH_TIMEOUT = 6
        const val ERROR_NO_MATCH = 7
        const val ERROR_RECOGNIZER_BUSY = 8
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
        const val ERROR_TOO_MANY_REQUESTS = 10
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
