package com.semo.keyboard.ime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.semo.keyboard.domain.model.KeyboardLanguage
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
        device: Locale = Locale.getDefault()
    ): String {
        val code = language.code
        if (code == "ar") {
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
    private var tag = "ar-SA"
    private var running = false
    private var options = VoiceOptions()
    private var vosk: VoskVoice? = null
    private val cues by lazy { VoiceCuePlayer(context) }
    private var beginTask: Runnable? = null

    private fun playCue(cue: VoiceCue): Boolean = runCatching { cues.play(cue, options.cueVolume) }.getOrDefault(false)

    private fun cancelBegin() {
        beginTask?.let { main.removeCallbacks(it) }
        beginTask = null
    }

    private fun beginWithCue(action: () -> Unit) {
        running = true
        if (!playCue(VoiceCue.START)) {
            action()
            return
        }
        val task = Runnable {
            beginTask = null
            if (running) action()
        }
        beginTask = task
        main.postDelayed(task, START_DELAY_MS)
    }

    fun release() {
        cancelBegin()
        runCatching { cues.release() }
    }

    private val engineCallback = object : Callback {
        override fun onStatus(status: VoiceStatus) {
            when (status) {
                VoiceStatus.PAUSED, VoiceStatus.FAILED, VoiceStatus.UNAVAILABLE, VoiceStatus.NETWORK, VoiceStatus.NO_PERMISSION -> running = false
                else -> Unit
            }
            callback.onStatus(status)
        }

        override fun onPartial(text: String) = callback.onPartial(text)
        override fun onFinal(text: String) = callback.onFinal(text)
        override fun onLevel(level: Float) = callback.onLevel(level)
    }

    private fun engineCode(): String = tag.substringBefore('-').lowercase(Locale.ROOT)

    private fun engineReady(): Boolean {
        val code = engineCode()
        return VoskModels.specFor(code) != null && VoskModelManager.isReady(context, code)
    }

    val active: Boolean get() = running

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(languageTag: String, voiceOptions: VoiceOptions = VoiceOptions()) {
        cancel()
        tag = languageTag
        options = voiceOptions
        if (!hasPermission()) {
            callback.onStatus(VoiceStatus.NO_PERMISSION)
            return
        }
        if (!engineReady()) {
            callback.onStatus(VoiceStatus.UNAVAILABLE)
            return
        }
        callback.onStatus(VoiceStatus.STARTING)
        beginWithCue {
            val engine = vosk ?: VoskVoice(context, engineCallback).also { vosk = it }
            engine.start(engineCode())
        }
    }

    fun finish() {
        cancelBegin()
        val engine = vosk
        if (engine == null || !engine.active) {
            running = false
            callback.onStatus(VoiceStatus.PAUSED)
            return
        }
        playCue(VoiceCue.END)
        engine.finish()
    }

    fun cancelByUser() {
        val wasActive = running
        cancel()
        if (wasActive) playCue(VoiceCue.CANCEL)
    }

    fun cancel() {
        cancelBegin()
        vosk?.cancel()
        running = false
    }

    private companion object {
        const val START_DELAY_MS = 170L
    }
}
