package com.semo.keyboard.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

enum class OfflinePack { INSTALLED, DOWNLOADABLE, PENDING, UNSUPPORTED }

object OfflineSpeech {

    private const val POLL_MS = 2500L
    private const val POLL_LIMIT = 90
    private val main = Handler(Looper.getMainLooper())

    val canManage: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun normalize(tag: String): String = tag.replace('_', '-').lowercase()

    private fun intentFor(tag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
    }

    private fun onDeviceReady(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    private fun matches(list: List<String>, tag: String): Boolean {
        val wanted = normalize(tag)
        val base = wanted.substringBefore('-')
        return list.any {
            val value = normalize(it)
            value == wanted || value.substringBefore('-') == base
        }
    }

    private fun exact(list: List<String>, tag: String): Boolean {
        val wanted = normalize(tag)
        return list.any { normalize(it) == wanted }
    }

    private fun classify(support: RecognitionSupport, tag: String): OfflinePack = when {
        exact(support.installedOnDeviceLanguages, tag) -> OfflinePack.INSTALLED
        matches(support.installedOnDeviceLanguages, tag) -> OfflinePack.INSTALLED
        matches(support.pendingOnDeviceLanguages, tag) -> OfflinePack.PENDING
        matches(support.supportedOnDeviceLanguages, tag) -> OfflinePack.DOWNLOADABLE
        else -> OfflinePack.UNSUPPORTED
    }

    fun check(context: Context, tag: String, onResult: (OfflinePack) -> Unit) {
        if (!onDeviceReady(context)) {
            onResult(OfflinePack.UNSUPPORTED)
            return
        }
        checkApi33(context.applicationContext, tag, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkApi33(context: Context, tag: String, onResult: (OfflinePack) -> Unit) {
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        if (recognizer == null) {
            onResult(OfflinePack.UNSUPPORTED)
            return
        }
        var done = false
        fun finish(pack: OfflinePack) {
            if (done) return
            done = true
            runCatching { recognizer.destroy() }
            onResult(pack)
        }
        val started = runCatching {
            recognizer.checkRecognitionSupport(
                intentFor(tag),
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        finish(classify(recognitionSupport, tag))
                    }

                    override fun onError(error: Int) {
                        finish(OfflinePack.UNSUPPORTED)
                    }
                }
            )
        }.isSuccess
        if (!started) finish(OfflinePack.UNSUPPORTED)
    }

    fun download(
        context: Context,
        tag: String,
        onProgress: (Int) -> Unit,
        onResult: (OfflinePack) -> Unit
    ) {
        if (!onDeviceReady(context)) {
            onResult(OfflinePack.UNSUPPORTED)
            return
        }
        downloadApi33(context.applicationContext, tag, onProgress, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun downloadApi33(
        context: Context,
        tag: String,
        onProgress: (Int) -> Unit,
        onResult: (OfflinePack) -> Unit
    ) {
        val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        if (recognizer == null) {
            onResult(OfflinePack.UNSUPPORTED)
            return
        }
        var done = false
        fun finish(pack: OfflinePack) {
            if (done) return
            done = true
            runCatching { recognizer.destroy() }
            onResult(pack)
        }
        fun poll(attempt: Int) {
            if (done) return
            check(context, tag) { pack ->
                when {
                    done -> Unit
                    pack == OfflinePack.INSTALLED -> finish(OfflinePack.INSTALLED)
                    attempt >= POLL_LIMIT -> finish(pack)
                    else -> main.postDelayed({ poll(attempt + 1) }, POLL_MS)
                }
            }
        }
        val intent = intentFor(tag)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                trigger34(
                    context,
                    recognizer,
                    intent,
                    onProgress,
                    { finish(OfflinePack.INSTALLED) },
                    { main.postDelayed({ poll(0) }, POLL_MS) },
                    { finish(OfflinePack.DOWNLOADABLE) }
                )
            } else {
                recognizer.triggerModelDownload(intent)
                main.postDelayed({ poll(0) }, POLL_MS)
            }
        }.isSuccess
        if (!started) finish(OfflinePack.UNSUPPORTED)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun trigger34(
        context: Context,
        recognizer: SpeechRecognizer,
        intent: Intent,
        progressCb: (Int) -> Unit,
        successCb: () -> Unit,
        scheduledCb: () -> Unit,
        errorCb: () -> Unit
    ) {
        recognizer.triggerModelDownload(
            intent,
            ContextCompat.getMainExecutor(context),
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    progressCb(completedPercent)
                }

                override fun onSuccess() {
                    successCb()
                }

                override fun onScheduled() {
                    scheduledCb()
                }

                override fun onError(error: Int) {
                    errorCb()
                }
            }
        )
    }

    fun openSystemSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.voicesearch.greco3.languagepack.InstallActivity"
                )
            ),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return true
        }
        return false
    }
}
