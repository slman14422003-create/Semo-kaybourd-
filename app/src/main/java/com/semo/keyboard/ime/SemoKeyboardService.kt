package com.semo.keyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.semo.keyboard.data.ClipFileStore
import com.semo.keyboard.data.ClipImageStore
import com.semo.keyboard.data.ClipLimits
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.ShortcutsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.ClipNotice
import com.semo.keyboard.domain.model.clipFileKey
import com.semo.keyboard.domain.model.clipImageKey
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.FieldKind
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.ui.keyboard.InputBridge
import com.semo.keyboard.ui.keyboard.KeyFeedback
import com.semo.keyboard.util.BlurSupport
import com.semo.keyboard.util.KeyboardStatusHelper
import com.semo.keyboard.ui.keyboard.KeyboardScreen
import com.semo.keyboard.ui.keyboard.KeyboardViewModel
import com.semo.keyboard.ui.keyboard.KeyboardViewModelFactory
import com.semo.keyboard.ui.MainActivity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineExceptionHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** أقصى عدد أحرف بدفعة لصق واحدة (النص الطويل يُلصق على دفعات كي لا يتجاوز حدّ حجم رسالة Binder) */
private const val COMMIT_CHUNK = 4_000
private const val NEURAL_IDLE_MS = 300_000L
private const val COMMIT_CHUNK_BIG = 24_000
private const val COMMIT_BIG_THRESHOLD = 200_000
private const val PASTE_TARGET_MS = 45L
private const val PASTE_MIN_CHUNK = 2_000
private const val PASTE_MAX_CHUNK = 64_000
private const val PASTE_PROGRESS_MIN = 40_000
private const val LARGE_COPY_CHARS = 150_000
private const val SLOW_BACKOFF_MS = 600L
private const val EXPLICIT_READ_MS = 250L
private const val FIELD_READ_MS = 1500L
private const val FIELD_READ_MAX = 4500
private const val START_READ_MS = 100L
private const val DELETE_FEEDBACK_GAP_MS = 35L
private const val POLL_IDLE_MS = 1200L
private const val SHOT_WINDOW_S = 300L
private val SHOT_POLL_DELAYS = longArrayOf(0L)
private val SHOT_SHOW_DELAYS = longArrayOf(0L, 700L)
private val SHOT_EVENT_DELAYS = longArrayOf(250L, 500L, 900L, 1600L, 2600L)
private val CLIP_FORCE_DELAYS = longArrayOf(0L, 60L, 150L, 300L, 600L, 1200L)
private val CLIP_POLL_DELAYS = longArrayOf(0L, 60L, 150L, 300L, 600L)
private const val CLIP_MAX_FAILS = 3
private const val HTML_CLIP_MAX = 2_000_000
private val IMG_SRC_REGEX = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

/**
 * خدمة لوحة المفاتيح. تستضيف واجهة Compose داخل نافذة الـ IME.
 *
 * أهم نقطة (وهي سبب أن اللوحة ما كانت تظهر): Compose يبحث عن LifecycleOwner و
 * SavedStateRegistryOwner و ViewModelStoreOwner على **جذر النافذة (decorView)** وليس
 * على الـ ComposeView فقط. لذلك نثبّتها على الاثنين، وإلا يفشل التركيب بصمت أو بكراش.
 */
class SemoKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var viewModel: KeyboardViewModel
    private lateinit var clipboardRepository: ClipboardRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    /** صوت ضغط شبيه بآيفون (مُولَّد برمجيًا) */
    private var clickPlayer: KeyClickPlayer? = null

    /** الصوت والاهتزاز على خيط خلفي كي لا يؤخّرا رسم الضغطة التالية على الخيط الرئيسي */
    private val feedbackThread = HandlerThread("semo-feedback", android.os.Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
    private val feedbackHandler = Handler(feedbackThread.looper)

    /** الاهتزاز بخيط مستقل عن الصوت: توقّف أحدهما (AudioTrack) لا يؤخّر الآخر */
    private val hapticThread = HandlerThread("semo-haptic", android.os.Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
    private val hapticHandler = Handler(hapticThread.looper)

    private val readThread = HandlerThread("semo-read", android.os.Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
    private val readHandler = Handler(readThread.looper)
    private val readsInFlight = AtomicInteger(0)
    @Volatile private var slowUntil = 0L
    @Volatile private var lastTypeAt = 0L
    @Volatile private var lastDeleteFeedbackAt = 0L
    private val capsPending = AtomicBoolean(false)
    private val pendingHaptic = AtomicReference<KeyFeedback?>(null)
    private var clipJob: Job? = null
    private var shotJob: Job? = null

    private class ReadTask<T>(private val block: () -> T) : Runnable {
        @Volatile var cancelled = false
        var value: T? = null
        val latch = CountDownLatch(1)
        override fun run() {
            try {
                if (!cancelled) value = runCatching(block).getOrNull()
            } finally {
                latch.countDown()
            }
        }
    }

    private fun <T> readWithin(timeoutMs: Long, skipIfSlow: Boolean, block: () -> T): T? {
        if (skipIfSlow && SystemClock.uptimeMillis() < slowUntil && readsInFlight.get() > 0) return null
        val task = ReadTask(block)
        readsInFlight.incrementAndGet()
        val posted = readHandler.post {
            try {
                task.run()
            } finally {
                readsInFlight.decrementAndGet()
            }
        }
        if (!posted) {
            readsInFlight.decrementAndGet()
            return null
        }
        val finished = try {
            task.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            task.cancelled = true
            slowUntil = SystemClock.uptimeMillis() + SLOW_BACKOFF_MS
            return null
        }
        return task.value
    }

    /**
     * قراءات حقل الإدخال (getCursorCapsMode...) استدعاءات بين عمليتين تتجمّد حتى يردّ التطبيق؛ كانت تُنفَّذ
     * على الخيط الرئيسي مع كل ضغطة فتتقطّع الكتابة بالتطبيقات الثقيلة. هلأ تُنفَّذ هنا.
     */
    private val ioThread = HandlerThread("semo-io").also { it.start() }
    private val ioHandler = Handler(ioThread.looper)

    private val aiThread = HandlerThread("semo-ai", android.os.Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
    private val aiHandler = Handler(aiThread.looper)
    @Volatile private var neuralRequested = false
    private val neuralUnload = Runnable {
        com.semo.keyboard.domain.logic.NeuralLm.unload()
        neuralRequested = false
    }

    private fun requestNeuralLm() {
        if (neuralRequested) return
        neuralRequested = true
        aiHandler.post { runCatching { com.semo.keyboard.domain.logic.NeuralLm.load(applicationContext) } }
    }

    /** هل يوجد نص محدد؟ نحدّثه من onUpdateSelection بدل سؤال التطبيق (getSelectedText) عند كل حذف */
    @Volatile private var hasSelection = false
    @Volatile private var caretPos = -1

    private val imageStore by lazy { ClipImageStore(applicationContext) }
    private val fileStore by lazy { ClipFileStore(applicationContext) }
    @Volatile private var pasteCancelled = false
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    private val hasVibrator: Boolean by lazy { vibrator?.hasVibrator() == true }

    private val hapticEffects: Map<KeyFeedback, VibrationEffect> by lazy { buildHapticEffects() }

    private fun buildHapticEffects(): Map<KeyFeedback, VibrationEffect> {
        val v = vibrator
        val amp = v?.hasAmplitudeControl() == true
        fun shot(ms: Long, strength: Int): VibrationEffect =
            VibrationEffect.createOneShot(ms, if (amp) strength else VibrationEffect.DEFAULT_AMPLITUDE)
        val legacy = mapOf(
            KeyFeedback.STANDARD to shot(8, 70),
            KeyFeedback.MODIFIER to shot(9, 90),
            KeyFeedback.SPACE to shot(9, 90),
            KeyFeedback.SELECTION to shot(6, 50),
            KeyFeedback.DELETE to shot(10, 120),
            KeyFeedback.LONG_PRESS to shot(16, 180)
        )
        if (v == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return legacy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            composedHaptics(v)?.let { return it }
            val support = v.areEffectsSupported(
                VibrationEffect.EFFECT_TICK,
                VibrationEffect.EFFECT_CLICK,
                VibrationEffect.EFFECT_HEAVY_CLICK
            )
            if (support.any { it == Vibrator.VIBRATION_EFFECT_SUPPORT_NO }) return legacy
        }
        val tick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        val heavy = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        return mapOf(
            KeyFeedback.STANDARD to tick,
            KeyFeedback.MODIFIER to tick,
            KeyFeedback.SPACE to tick,
            KeyFeedback.SELECTION to tick,
            KeyFeedback.DELETE to click,
            KeyFeedback.LONG_PRESS to heavy
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun composedHaptics(v: Vibrator): Map<KeyFeedback, VibrationEffect>? {
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        if (!v.areAllPrimitivesSupported(tick, click)) return null
        val low = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
        ) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else tick
        fun one(id: Int, scale: Float): VibrationEffect =
            VibrationEffect.startComposition().addPrimitive(id, scale).compose()
        return mapOf(
            KeyFeedback.STANDARD to one(tick, 0.8f),
            KeyFeedback.MODIFIER to one(low, 0.9f),
            KeyFeedback.SPACE to one(low, 0.9f),
            KeyFeedback.SELECTION to one(tick, 0.45f),
            KeyFeedback.DELETE to one(click, 0.55f),
            KeyFeedback.LONG_PRESS to one(click, 1f)
        )
    }

    private val hapticRunner = Runnable {
        val kind = pendingHaptic.getAndSet(null) ?: return@Runnable
        runCatching { vibrateTick(kind) }
    }

    private fun requestHaptic(kind: KeyFeedback) {
        val previous = pendingHaptic.getAndUpdate { prev -> if (prev == KeyFeedback.LONG_PRESS) prev else kind }
        if (previous == null) hapticHandler.post(hapticRunner)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cursorMovedTask = Runnable {
        if (::viewModel.isInitialized && capsPending.compareAndSet(false, true)) {
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val ic = currentInputConnection
            readHandler.post {
                val capitalize = try {
                    runCatching { shouldCapitalize(inputType, ic) }.getOrDefault(false)
                } finally {
                    capsPending.set(false)
                }
                mainHandler.post {
                    if (::viewModel.isInitialized) viewModel.onCursorMoved(capitalize)
                }
            }
        }
    }

    /** حقل كلمة مرور: لا نسجّل الحافظة ولا نتعلّم كلمات */
    private var passwordField = false
    private var lastCapturedClip: String? = null
    private var lastClipTs = 0L

    private var lastShotId = -1L

    /** استطلاع دوري أثناء ظهور اللوحة: بعض الأنظمة (HyperOS/One UI) لا ترسل إشعار تغيّر الحافظة بشكل موثوق */
    private var pollTick = 0
    private val pollTask = object : Runnable {
        override fun run() {
            if (::viewModel.isInitialized && SystemClock.uptimeMillis() - lastTypeAt > POLL_IDLE_MS) {
                captureClipboard()
                // فحص الحافظة خفيف (بصمة زمن فقط)، لكن فحص لقطات الشاشة يسأل MediaStore فعليًا
                // (أثقل)؛ نشغّله كل دورتين بدل كل دورة لتخفيف الحمل على التخزين دون تأخير محسوس
                pollTick++
                if (pollTick % 2 == 0) captureScreenshot(SHOT_POLL_DELAYS)
            }
            mainHandler.postDelayed(this, 2500)
        }
    }

    private fun hasMediaPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return androidx.core.content.ContextCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private class Shot(val id: Long, val uri: android.net.Uri, val mime: String)

    private fun latestScreenshot(): Shot? {
        val media = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cutoff = System.currentTimeMillis() / 1000 - SHOT_WINDOW_S
        val nameCol = android.provider.MediaStore.Images.Media.DISPLAY_NAME
        val like = if (Build.VERSION.SDK_INT >= 29)
            "($nameCol LIKE '%creenshot%' OR $nameCol LIKE 'Screen%' OR ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE '%creenshot%')"
        else "(${android.provider.MediaStore.Images.Media.DATA} LIKE '%creenshot%' OR $nameCol LIKE 'Screen%')"
        val sel = "${android.provider.MediaStore.Images.Media.DATE_ADDED} >= ? AND $like"
        val extras = android.os.Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, sel)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(cutoff.toString()))
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(android.provider.MediaStore.Images.Media.DATE_ADDED, android.provider.MediaStore.Images.Media._ID)
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
        }
        contentResolver.query(
            media,
            arrayOf(android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.MIME_TYPE),
            extras,
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val mime = c.getString(1) ?: "image/png"
                return Shot(id, android.content.ContentUris.withAppendedId(media, id), mime)
            }
        }
        return null
    }

    private val shotTask = Runnable { captureScreenshot(SHOT_EVENT_DELAYS) }

    private val shotObserver = object : android.database.ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(shotTask)
            mainHandler.postDelayed(shotTask, 200)
        }
    }

    private fun captureScreenshot(delaysMs: LongArray = SHOT_POLL_DELAYS) {
        if (!::viewModel.isInitialized || passwordField || !viewModel.uiState.value.clipboardEnabled || !hasMediaPermission()) return
        if (shotJob?.isActive == true) return
        shotJob = serviceScope.launch {
            for (wait in delaysMs) {
                if (wait > 0L) delay(wait)
                val shot = withContext(Dispatchers.IO) { runCatching { latestScreenshot() }.getOrNull() } ?: continue
                if (shot.id == lastShotId) return@launch
                val name = withContext(Dispatchers.IO) {
                    runCatching { imageStore.saveFromUri(shot.uri, shot.mime) }.getOrNull()
                } ?: continue
                val stored = clipboardRepository.add(clipImageKey(name, shot.mime)) ?: continue
                lastShotId = shot.id
                viewModel.onClipboardCopied(stored)
                return@launch
            }
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val early = runCatching { (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip }.getOrNull()
        captureClipboard(force = true, early = early)
    }

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        com.semo.keyboard.diag.CrashLog.install(applicationContext)
        BlurSupport.attach(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        clickPlayer = KeyClickPlayer(applicationContext)
        startCacheCycle()
        prewarmAssets()
        val settingsRepository = SettingsRepository(applicationContext)
        ioHandler.post { runCatching { kotlinx.coroutines.runBlocking { settingsRepository.syncAppIcon() } } }
        clipboardRepository = ClipboardRepository(applicationContext)
        val learnedRepository = LearnedWordsRepository(applicationContext)
        val shortcutsRepository = ShortcutsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            KeyboardViewModelFactory(settingsRepository, clipboardRepository, learnedRepository, shortcutsRepository, bridge)
        )[KeyboardViewModel::class.java]

        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipListener)
        runCatching {
            contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, shotObserver)
        }

        // القاموس الكبير (لو نُزِّل) يُحمَّل بخيط خلفي كي لا يؤخّر ظهور اللوحة
        ioHandler.post { runCatching { com.semo.keyboard.domain.logic.AutoCorrect.attach(applicationContext) } }
        ioHandler.post { runCatching { com.semo.keyboard.domain.logic.EmojiSearch.attach(applicationContext) } }
        ioHandler.post { runCatching { com.semo.keyboard.domain.logic.SentenceRules.attach(applicationContext) } }
        ioHandler.post { runCatching { DictionaryManager.loadAll(applicationContext) } }
        serviceScope.launch {
            viewModel.uiState
                .map { it.language }
                .distinctUntilChanged()
                .collect {
                    if (it == com.semo.keyboard.domain.model.KeyboardLanguage.ARABIC) {
                        aiHandler.removeCallbacks(neuralUnload)
                        requestNeuralLm()
                    } else if (neuralRequested) {
                        aiHandler.removeCallbacks(neuralUnload)
                        aiHandler.postDelayed(neuralUnload, NEURAL_IDLE_MS)
                    }
                }
        }
        ioHandler.post { runCatching { VoskModelManager.cleanLeftovers(applicationContext) } }
        hapticHandler.post { runCatching { if (hasVibrator) hapticEffects.size } }
    }

    private class ClipSnap(
        val text: String?,
        val uri: android.net.Uri?,
        val imageMime: String?,
        val declaredMime: String? = null,
        val asFile: Boolean = false
    )

    private fun clipKey(text: String): String {
        var h = -3750763034362895579L
        for (c in text) h = (h xor c.code.toLong()) * 1099511628211L
        return "t${text.length}:${h.toString(16)}"
    }

    /** يقرأ الحافظة بخيط خلفي (النص الطويل قد يتأخر) ويرجع null لو فاضية أو حساسة */
    private fun readClip(early: ClipData? = null): ClipSnap? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = early ?: cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        if (Build.VERSION.SDK_INT >= 33) {
            val sensitive = clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            if (sensitive) return null
        }
        val desc = clip.description
        val items = (0 until clip.itemCount).map { clip.getItemAt(it) }
        val descImageMime = (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }.firstOrNull { it.startsWith("image/") }
        val texts = items.mapNotNull { runCatching { it.text?.toString() }.getOrNull()?.takeIf { s -> s.isNotBlank() } }
        val plain = when (texts.size) {
            0 -> null
            1 -> texts[0]
            else -> texts.distinct().joinToString("\n")
        }
        val htmlRaw = if (plain == null) {
            items.firstNotNullOfOrNull { runCatching { it.htmlText }.getOrNull()?.takeIf { s -> s.isNotBlank() && s.length <= HTML_CLIP_MAX } }
        } else null
        val html = htmlRaw
            ?.let { runCatching { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }.getOrNull() }
            ?.replace("\uFFFC", "")
            ?.takeIf { it.isNotBlank() }
        val htmlImageUrl = if (plain == null && html == null && htmlRaw != null) {
            IMG_SRC_REGEX.find(htmlRaw)?.groupValues?.get(1)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        } else null
        val contentUris = items.mapNotNull { item -> item.uri?.takeIf { it.scheme == "content" } }
        val uri = contentUris.firstOrNull { resolveImageMime(descImageMime, it) != null } ?: contentUris.firstOrNull()
        val plainTrim = plain?.trim()
        val singleWebLink = plainTrim != null && plainTrim.length <= 2048 && !plainTrim.contains(' ') && !plainTrim.contains('\n') &&
            (plainTrim.startsWith("http://") || plainTrim.startsWith("https://"))
        val plainLooksLikeUri = plainTrim == null ||
            plainTrim.startsWith("content:") || plainTrim.startsWith("file:") || plainTrim.startsWith("/") ||
            plainTrim == uri?.toString() ||
            (descImageMime != null && singleWebLink)
        val mime = if (uri != null && plainLooksLikeUri) resolveImageMime(descImageMime, uri) else null
        val fileText = if (mime == null && uri != null && plainLooksLikeUri) readTextUri(uri) else null
        val coerced = if (plain == null && html == null && fileText == null && mime == null) {
            items.firstNotNullOfOrNull { runCatching { it.coerceToText(this)?.toString() }.getOrNull()?.takeIf { s -> s.isNotBlank() && !s.startsWith("content:") && !s.startsWith("file:") } }
        } else null
        val linkText = if (plain == null && html == null && fileText == null && coerced == null) {
            items.firstNotNullOfOrNull { item -> item.uri?.takeIf { it.scheme != null && it.scheme != "content" && it.scheme != "file" }?.toString() }
        } else null
        val textOut = when {
            mime != null -> null
            fileText != null -> fileText
            plain != null && !(plainTrim!!.startsWith("content:") || plainTrim.startsWith("file:")) -> plain
            else -> html ?: coerced ?: linkText ?: htmlImageUrl
        }
        val declared = (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }
            .firstOrNull { !it.endsWith("/*") && it != ClipDescription.MIMETYPE_TEXT_URILIST }
        val asFile = textOut == null && mime == null && uri != null && plainLooksLikeUri
        return ClipSnap(textOut, uri, mime, declared, asFile)
    }

    /**
     * يحفظ آخر شيء نُسخ بسجل الحافظة (إلا بحقول كلمات المرور أو لو علّم التطبيق النص كحساس).
     * القراءة والحفظ بخيط خلفي مع إعادة محاولة (الحافظة قد لا تكون جاهزة لحظة الإشعار)، والنص الطويل يُحفظ كاملًا بملف.
     */
    private var clipFailKey: String? = null
    private var clipFailCount = 0

    private fun noteClipFailure(key: String): Boolean {
        if (clipFailKey != key) {
            clipFailKey = key
            clipFailCount = 0
        }
        clipFailCount++
        return clipFailCount >= CLIP_MAX_FAILS
    }

    private fun captureClipboard(force: Boolean = false, early: ClipData? = null) {
        if (passwordField || !viewModel.uiState.value.clipboardEnabled) return
        if (!force && clipJob?.isActive == true) return
        clipJob = serviceScope.launch {
            val ts = withContext(Dispatchers.IO) {
                runCatching {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClipDescription?.timestamp
                }.getOrNull() ?: 0L
            }
            if (!force && ts != 0L && ts == lastClipTs) return@launch
            var snap: ClipSnap? = null
            val delays = if (force) CLIP_FORCE_DELAYS else CLIP_POLL_DELAYS
            for (attempt in delays.indices) {
                if (delays[attempt] > 0L) delay(delays[attempt])
                val first = if (attempt == 0) early else null
                snap = withContext(Dispatchers.IO) { runCatching { readClip(first) }.getOrNull() }
                if (snap != null && (snap.text != null || snap.imageMime != null || snap.asFile)) break
            }
            val s = snap ?: return@launch
            fun markHandled() {
                if (ts != 0L) lastClipTs = ts
            }
            if (s.imageMime != null && s.uri != null) {
                val id = s.uri.toString()
                if (!force && id == lastCapturedClip) {
                    markHandled()
                    return@launch
                }
                val name = withContext(Dispatchers.IO) { runCatching { imageStore.saveFromUri(s.uri, s.imageMime) }.getOrNull() }
                val stored = if (name != null) clipboardRepository.add(clipImageKey(name, s.imageMime)) else null
                if (stored == null) {
                    if (name == null && imageStore.lastTooLarge) {
                        lastCapturedClip = id
                        markHandled()
                        viewModel.onClipFailed(ClipNotice.TOO_LARGE)
                    } else if (noteClipFailure(id)) {
                        markHandled()
                        viewModel.onClipFailed(ClipNotice.EXPIRED)
                    }
                    return@launch
                }
                lastCapturedClip = id
                markHandled()
                viewModel.onClipboardCopied(stored)
                return@launch
            }
            if (s.asFile && s.uri != null) {
                val id = s.uri.toString()
                if (!force && id == lastCapturedClip) {
                    markHandled()
                    return@launch
                }
                val limit = ClipLimits.fileBytes(applicationContext)
                val result = withContext(Dispatchers.IO) {
                    runCatching { fileStore.saveFromUri(s.uri, s.declaredMime, limit) }.getOrDefault(ClipFileStore.Result.Failed)
                }
                when (result) {
                    is ClipFileStore.Result.Saved -> {
                        val stored = clipboardRepository.add(clipFileKey(result.name, result.mime, result.size, result.label))
                        if (stored == null) {
                            if (noteClipFailure(id)) {
                                markHandled()
                                viewModel.onClipFailed(ClipNotice.STORAGE_FULL)
                            }
                            return@launch
                        }
                        clipFailKey = null
                        lastCapturedClip = id
                        markHandled()
                        viewModel.onClipboardCopied(stored)
                    }
                    is ClipFileStore.Result.TooLarge -> {
                        lastCapturedClip = id
                        markHandled()
                        viewModel.onClipFailed(ClipNotice.TOO_LARGE)
                    }
                    is ClipFileStore.Result.NoSpace -> {
                        lastCapturedClip = id
                        markHandled()
                        viewModel.onClipFailed(ClipNotice.STORAGE_FULL)
                    }
                    is ClipFileStore.Result.Failed -> {
                        if (noteClipFailure(id)) {
                            markHandled()
                            viewModel.onClipFailed(ClipNotice.EXPIRED)
                        }
                    }
                }
                return@launch
            }
            val text = s.text
            if (text == null || text.isBlank()) {
                markHandled()
                return@launch
            }
            val key = withContext(Dispatchers.Default) { clipKey(text) }
            if (!force && key == lastCapturedClip) {
                markHandled()
                return@launch
            }
            if (text.length > ClipboardRepository.MAX_TEXT_LENGTH) {
                lastCapturedClip = key
                markHandled()
                viewModel.onClipFailed(ClipNotice.TOO_LARGE)
                return@launch
            }
            var stored = clipboardRepository.add(text)
            if (stored == null) {
                delay(300)
                stored = clipboardRepository.add(text)
            }
            if (stored == null) {
                if (noteClipFailure(key)) {
                    markHandled()
                    viewModel.onClipFailed(ClipNotice.STORAGE_FULL)
                }
                return@launch
            }
            clipFailKey = null
            lastCapturedClip = key
            markHandled()
            viewModel.onClipboardCopied(stored)
        }
    }

    private fun readTextUri(uri: android.net.Uri): String? {
        val type = runCatching { contentResolver.getType(uri) }.getOrNull().orEmpty()
        val generic = type.isEmpty() || type == "application/octet-stream" || type.endsWith("/*")
        val ext = if (generic) fileStore.displayNameOf(uri)?.substringAfterLast('.', "")?.lowercase().orEmpty() else ""
        val textual = type.startsWith("text/") || type == "application/json" || type == "application/xml" ||
            type == "application/javascript" || type == "application/x-sh" || (generic && ext in ClipFileStore.TEXT_EXTENSIONS)
        if (!textual || type == "text/uri-list") return null
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val limit = ClipboardRepository.MAX_TEXT_LENGTH
                val reader = stream.bufferedReader(Charsets.UTF_8)
                val sb = StringBuilder()
                val buf = CharArray(16 * 1024)
                while (sb.length <= limit) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString().takeIf { it.isNotBlank() && it.length <= limit }
            }
        }.getOrNull()
    }

    /** نوع الصورة: من مزوّد المحتوى أولًا (الأدق)، ثم من وصف المقطع؛ null لو الرابط ليس صورة */
    private fun resolveImageMime(descMime: String?, uri: android.net.Uri): String? {
        val provider = runCatching { contentResolver.getType(uri) }.getOrNull()
            ?.takeIf { it.startsWith("image/") && !it.endsWith("/*") }
        if (provider != null) return provider
        if (descMime != null) return descMime
        // المزوّد لا يُعلن نوع الملف: نفحص المحتوى فعليًا (لقطات الشاشة من بعض الأنظمة)
        return runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            bounds.outMimeType?.takeIf { it.startsWith("image/") && bounds.outWidth > 0 }
        }.getOrNull() ?: ClipFileStore.sniffImageMime(contentResolver, uri)
    }

    private var voiceInput: VoiceInput? = null
    private var voiceComposing = false
    private var voicePrefixValue: String? = null
    private var pendingVoice = false

    private val voiceCallback = object : VoiceInput.Callback {
        override fun onStatus(status: com.semo.keyboard.domain.model.VoiceStatus) {
            if (!::viewModel.isInitialized) return
            viewModel.onVoiceStatus(status)
            if (status != com.semo.keyboard.domain.model.VoiceStatus.LISTENING &&
                status != com.semo.keyboard.domain.model.VoiceStatus.STARTING
            ) commitVoiceLeftover()
        }

        override fun onPartial(text: String) {
            voiceCompose(text)
            if (::viewModel.isInitialized) viewModel.onVoicePartial(text)
        }

        override fun onFinal(text: String) {
            voiceCommit(text)
            if (::viewModel.isInitialized) viewModel.onVoicePartial("")
        }

        override fun onLevel(level: Float) {
            if (::viewModel.isInitialized) viewModel.onVoiceLevel(level)
        }
    }

    private fun voicePrefixFor(ic: android.view.inputmethod.InputConnection): String {
        val before = runCatching { ic.getTextBeforeCursor(1, 0)?.toString() }.getOrNull().orEmpty()
        val last = before.lastOrNull() ?: return ""
        return if (last.isWhitespace() || last in "([{\"'") "" else " "
    }

    private fun voiceCompose(text: String) {
        val ic = currentInputConnection ?: return
        val prefix = voicePrefixValue ?: voicePrefixFor(ic).also { voicePrefixValue = it }
        voiceComposing = true
        lastTypeAt = SystemClock.uptimeMillis()
        runCatching { ic.setComposingText(prefix + text, 1) }
    }

    private fun voiceCommit(text: String) {
        val ic = currentInputConnection ?: return
        val prefix = voicePrefixValue ?: voicePrefixFor(ic)
        voicePrefixValue = null
        voiceComposing = false
        lastTypeAt = SystemClock.uptimeMillis()
        runCatching { ic.commitText(prefix + text, 1) }
    }

    private fun commitVoiceLeftover() {
        if (voiceComposing) {
            runCatching { currentInputConnection?.finishComposingText() }
        }
        voiceComposing = false
        voicePrefixValue = null
    }

    private fun startVoice(languageTag: String, options: com.semo.keyboard.domain.model.VoiceOptions) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (::viewModel.isInitialized) viewModel.onVoiceStatus(com.semo.keyboard.domain.model.VoiceStatus.NO_PERMISSION)
            VoiceBus.onPermission = { granted ->
                if (granted) {
                    pendingVoice = true
                    mainHandler.post { if (isInputViewShown) startPendingVoice() }
                }
            }
            runCatching {
                startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
            return
        }
        val engine = voiceInput ?: VoiceInput(applicationContext, voiceCallback).also { voiceInput = it }
        engine.start(languageTag, options)
    }

    private fun startPendingVoice() {
        pendingVoice = false
        if (::viewModel.isInitialized) viewModel.openVoice()
    }

    private fun detectNavButtons(): Boolean = com.semo.keyboard.util.NavigationMode.usesButtons(this)

    private fun startCacheCycle() {
        serviceScope.launch {
            while (isActive) {
                val wait = com.semo.keyboard.data.CacheCleaner.millisUntilNext(applicationContext)
                if (wait > 0L) delay(wait)
                if (::clipboardRepository.isInitialized) runCatching { clipboardRepository.pruneOrphans() }
                if (com.semo.keyboard.data.CacheCleaner.isAutoEnabled(applicationContext)) {
                    com.semo.keyboard.data.CacheCleaner.clearNow(applicationContext)
                } else {
                    delay(com.semo.keyboard.data.CacheCleaner.INTERVAL_MS)
                }
                delay(60_000L)
            }
        }
    }

    private fun prewarmAssets() {
        ioHandler.post {
            runCatching { com.semo.keyboard.ui.keyboard.supportedEmojiCache.size }
        }
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setContent { KeyboardScreen(viewModel = viewModel, onChrome = ::applyChrome, onBlur = ::applyBlur) }
        }
        blurApplied = -1
        installViewTreeOwners(composeView)
        window?.window?.decorView?.let { installViewTreeOwners(it) }
        // نافذة شفافة كي تظهر زوايا اللوحة المدورة بنمط iOS 26
        window?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return composeView
    }

    private var blurApplied = -1

    private fun applyBlur(radiusDp: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val w = window?.window ?: return
        val px = (radiusDp * resources.displayMetrics.density).toInt().coerceAtLeast(0)
        if (px == blurApplied) return
        blurApplied = px
        runCatching { w.setBackgroundBlurRadius(px) }
    }

    private var chromeColor = 0
    private var chromeDark: Boolean? = null

    /**
     * يلوّن شريط التنقل بلون اللوحة ويضبط تباين أيقوناته (زر إخفاء اللوحة ومؤشر الإيماءات).
     * بدون هذا كانت أيقونات النظام تختلط بلون اللوحة خصوصًا بين الثيم الفاتح والداكن.
     */
    @Suppress("DEPRECATION")
    private fun applyChrome(panelColor: Int, dark: Boolean) {
        if (panelColor == chromeColor && dark == chromeDark) return
        val w = window?.window ?: return
        chromeColor = panelColor
        chromeDark = dark
        runCatching {
            w.navigationBarColor = panelColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = !dark
        }
    }

    private fun installViewTreeOwners(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    /** لا نريد وضع ملء الشاشة بالوضع الأفقي (كان يخفي اللوحة خلف حقل نصي كبير) */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val page = when (inputClass) {
            // حقول الأرقام والهاتف: لوحة الأرقام 3×4 (أرقام + عمليات + رموز شائعة)، والتاريخ والوقت: صفحة الرموز
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> KeyboardPage.NUMPAD
            else -> KeyboardPage.LETTERS
        }
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isTextClass = inputClass == InputType.TYPE_CLASS_TEXT
        passwordField = (isTextClass && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noSuggestions = passwordField ||
            inputClass != InputType.TYPE_CLASS_TEXT ||
            (isTextClass && (
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                ))
        // the "no personalized learning" flag (incognito tabs, secure messengers) only forbids learning, not corrections
        val learningAllowed = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0

        val fieldKind = when {
            isTextClass && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) -> FieldKind.EMAIL
            isTextClass && variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            else -> FieldKind.TEXT
        }
        KeyboardStatusHelper.markImeSeen(this)
        hasSelection = (info?.initialSelStart ?: 0) != (info?.initialSelEnd ?: 0)
        caretPos = info?.initialSelEnd ?: -1
        val startIc = currentInputConnection
        val startCaps = if (inputType == 0) false else readWithin(START_READ_MS, true) { shouldCapitalize(inputType, startIc) }
        voiceInput?.cancel()
        voiceComposing = false
        voicePrefixValue = null
        viewModel.setNavSetting(detectNavButtons())
        val startEnter = enterKindFor(info)
        viewModel.onStartInput(
            page, startCaps == true, startEnter, !noSuggestions, fieldKind,
            com.semo.keyboard.domain.logic.InputProfile.classify(info?.packageName, startEnter == EnterKind.SEARCH),
            learningAllowed
        )
        if (startCaps == null) {
            mainHandler.removeCallbacks(cursorMovedTask)
            mainHandler.postDelayed(cursorMovedTask, 150)
        }
        captureClipboard()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
        caretPos = newSelEnd
        // نجمع تحديثات المؤشر المتتالية (تصل مع كل حرف) بمهمة واحدة، لأن كل واحدة تكلّف استدعاءات للتطبيق
        mainHandler.removeCallbacks(cursorMovedTask)
        mainHandler.postDelayed(cursorMovedTask, 70)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        KeyboardStatusHelper.setImeVisible(this, true)
        if (::viewModel.isInitialized) viewModel.setNavSetting(detectNavButtons())
        captureClipboard()
        captureScreenshot(SHOT_SHOW_DELAYS)
        mainHandler.removeCallbacks(pollTask)
        mainHandler.postDelayed(pollTask, 600)
        if (pendingVoice) mainHandler.postDelayed({ if (pendingVoice && isInputViewShown) startPendingVoice() }, 250)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (::viewModel.isInitialized) {
            viewModel.closeVoice()
            viewModel.flushLearning()
        }
        mainHandler.removeCallbacks(pollTask)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        KeyboardStatusHelper.setImeVisible(this, false)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // لو ما صفّرنا هاد العلم بعد الخروج من حقل كلمة مرور، بيضل عالق true ويمنع التقاط
        // النسخ من أي تطبيق ثاني لاحقًا (حتى لو مو حقل كلمة مرور إطلاقًا) إلى حين فتح حقل جديد.
        passwordField = false
        voiceInput?.cancel()
    }

    /** صور الإيموجي الجاهزة تأخذ عشرات الميغابايت: نحرّرها لما يطلب النظام ذاكرة (اللوحة مخفية) */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.semo.keyboard.ui.keyboard.EmojiBitmapCache.clear()
            com.semo.keyboard.ime.VoskModelManager.trim()
            com.semo.keyboard.data.CacheCleaner.clearMemory()
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            com.semo.keyboard.data.CacheCleaner.clearMemory()
        }
    }

    override fun onDestroy() {
        BlurSupport.detach()
        runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(clipListener)
        }
        runCatching { contentResolver.unregisterContentObserver(shotObserver) }
        clipJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        voiceInput?.cancel()
        voiceInput?.release()
        voiceInput = null
        VoskModelManager.release()
        serviceScope.cancel()
        feedbackHandler.post {
            clickPlayer?.release()
            clickPlayer = null
        }
        feedbackThread.quitSafely()
        hapticHandler.removeCallbacksAndMessages(null)
        hapticThread.quitSafely()
        readHandler.removeCallbacksAndMessages(null)
        readThread.quitSafely()
        ioThread.quitSafely()
        aiHandler.removeCallbacksAndMessages(null)
        aiThread.quitSafely()
        KeyboardStatusHelper.setImeVisible(this, false)
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    private fun shouldCapitalize(
        inputType: Int,
        ic: android.view.inputmethod.InputConnection? = currentInputConnection
    ): Boolean = inputType != 0 && (ic?.getCursorCapsMode(inputType) ?: 0) != 0

    private fun enterKindFor(info: EditorInfo?): EnterKind {
        if (info == null) return EnterKind.RETURN
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterKind.RETURN
        if (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return EnterKind.RETURN
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
            EditorInfo.IME_ACTION_NEXT -> EnterKind.NEXT
            else -> EnterKind.RETURN
        }
    }

    // ---------- تنفيذ الأوامر على حقل الإدخال ----------

    private val bridge = object : InputBridge {

        override fun commitText(text: String) {
            lastTypeAt = SystemClock.uptimeMillis()
            val ic = currentInputConnection ?: return
            if (text.length <= COMMIT_CHUNK && !pasteMutex.isLocked) {
                runCatching { ic.commitText(text, 1) }
                return
            }
            // النص الطويل (سجلات، أكواد، مقالات): الاتصال بالتطبيق الهدف عبر Binder وحجم الرسالة الواحدة محدود (~1MB)،
            // فنلصقه على دفعات داخل عملية تحرير واحدة (تراجع واحد) مع تسليم الخيط للواجهة بين كل دفعة كي لا تتجمّد
            serviceScope.launch {
                pasteMutex.withLock { commitChunked(ic, text) }
            }
        }

        override fun commitPair(open: String, close: String) {
            lastTypeAt = SystemClock.uptimeMillis()
            val ic = currentInputConnection ?: return
            runCatching {
                ic.beginBatchEdit()
                ic.commitText(open, 1)
                ic.commitText(close, 0)
                ic.endBatchEdit()
            }
        }

        override fun stepOverClosing(close: String): Boolean {
            val ic = currentInputConnection ?: return false
            return runCatching {
                if (ic.getTextAfterCursor(close.length, 0)?.toString() != close) return@runCatching false
                ic.beginBatchEdit()
                ic.deleteSurroundingText(0, close.length)
                ic.commitText(close, 1)
                ic.endBatchEdit()
                true
            }.getOrDefault(false)
        }

        override fun deletePair(close: String): Boolean {
            val ic = currentInputConnection ?: return false
            return runCatching {
                if (ic.getTextAfterCursor(close.length, 0)?.toString() != close) return@runCatching false
                ic.deleteSurroundingText(1, close.length)
                true
            }.getOrDefault(false)
        }

        override fun deleteBackward() {
            val ic = currentInputConnection ?: return
            runCatching {
                // لو في نص محدد نحذفه كله، وإلا نرسل DEL حقيقي (يتعامل صح مع الإيموجي وأزواج الـ surrogate).
                // حالة التحديد تأتينا من onUpdateSelection؛ سؤال التطبيق (getSelectedText) بكل حذف كان يجمّد الواجهة.
                if (hasSelection) {
                    ic.commitText("", 1)
                    hasSelection = false
                } else {
                    sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
                }
            }
        }

        override fun deleteSurrounding(count: Int) {
            if (count <= 0) return
            runCatching { currentInputConnection?.deleteSurroundingText(count, 0) }
        }

        override fun moveCursor(delta: Int) {
            if (delta == 0) return
            val ic = currentInputConnection ?: return
            runCatching {
                val probe = ic.getTextAfterCursor(1, 0)?.firstOrNull() ?: ic.getTextBeforeCursor(1, 0)?.lastOrNull()
                val rtl = probe != null && Character.getDirectionality(probe).let {
                    it == Character.DIRECTIONALITY_RIGHT_TO_LEFT || it == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                }
                val d = if (rtl) -delta else delta
                val moved = if (d > 0) (ic.getTextAfterCursor(d, 0)?.length ?: 0) else -(ic.getTextBeforeCursor(-d, 0)?.length ?: 0)
                if (moved == 0) return
                val base = caretPos
                if (base >= 0) {
                    val target = (base + moved).coerceAtLeast(0)
                    ic.setSelection(target, target)
                    caretPos = target
                } else {
                    val code = if (d > 0) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    repeat(abs(d)) { sendDownUpKeyEvents(code) }
                }
            }
        }

        override fun moveCursorVertical(lines: Int) {
            if (lines == 0) return
            val code = if (lines > 0) android.view.KeyEvent.KEYCODE_DPAD_DOWN else android.view.KeyEvent.KEYCODE_DPAD_UP
            runCatching { repeat(abs(lines)) { sendDownUpKeyEvents(code) } }
        }

        override fun startVoiceInput() {
            this@SemoKeyboardService.switchToVoiceInput()
        }

        override fun voiceStart(languageTag: String, options: com.semo.keyboard.domain.model.VoiceOptions) =
            this@SemoKeyboardService.startVoice(languageTag, options)

        override fun voiceFinish() {
            voiceInput?.finish()
        }

        override fun voiceCancel() {
            voiceInput?.cancelByUser()
            commitVoiceLeftover()
        }

        override fun performEdit(action: EditAction) {
            val ic = currentInputConnection ?: return
            runCatching {
                when (action) {
                    EditAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
                    EditAction.CUT -> ic.performContextMenuAction(android.R.id.cut)
                    EditAction.COPY -> ic.performContextMenuAction(android.R.id.copy)
                    EditAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)
                    EditAction.LEFT -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                    EditAction.RIGHT -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                    EditAction.UP -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_UP)
                    EditAction.DOWN -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                    EditAction.HOME -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_MOVE_HOME)
                    EditAction.END -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_MOVE_END)
                    EditAction.UNDO -> ic.performContextMenuAction(android.R.id.undo)
                    EditAction.REDO -> ic.performContextMenuAction(android.R.id.redo)
                }
            }
        }

        override fun openSettings() {
            runCatching {
                startActivity(Intent(this@SemoKeyboardService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                requestHideSelf(0)
            }
        }

        override fun openVoiceSettings() {
            runCatching {
                startActivity(
                    Intent(this@SemoKeyboardService, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_PAGE, MainActivity.PAGE_VOICE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                requestHideSelf(0)
            }
        }

        override fun performEnter() {
            val ic = currentInputConnection ?: return
            val info = currentInputEditorInfo
            runCatching {
                val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                val noAction = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                val multiLine = ((info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                if (!noAction && !multiLine && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(action)
                } else {
                    sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
                }
            }
        }

        override fun switchKeyboard() {
            runCatching {
                val switched = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToNextInputMethod(false)
                if (!switched) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
                }
            }
        }

        override fun hideKeyboard() {
            runCatching { requestHideSelf(0) }
        }

        override fun textBeforeCursor(length: Int): String =
            textBeforeCursorWithin(length, EXPLICIT_READ_MS, false).orEmpty()

        override fun textAfterCursor(length: Int): String {
            val ic = currentInputConnection ?: return ""
            return readWithin(EXPLICIT_READ_MS, false) { ic.getTextAfterCursor(length, 0)?.toString().orEmpty() }.orEmpty()
        }

        override fun replaceBeforeCursor(deleteCount: Int, text: String) {
            lastTypeAt = SystemClock.uptimeMillis()
            val ic = currentInputConnection ?: return
            runCatching {
                ic.beginBatchEdit()
                if (deleteCount > 0) ic.deleteSurroundingText(deleteCount, 0)
                ic.commitText(text, 1)
                ic.endBatchEdit()
            }
        }

        override fun fieldText(): String {
            val ic = currentInputConnection ?: return ""
            return readWithin(FIELD_READ_MS, false) {
                val before = ic.getTextBeforeCursor(FIELD_READ_MAX, 0)?.toString().orEmpty()
                val after = ic.getTextAfterCursor(FIELD_READ_MAX, 0)?.toString().orEmpty()
                before + after
            }.orEmpty()
        }

        override fun replaceFieldText(text: String) {
            val ic = currentInputConnection ?: return
            lastTypeAt = SystemClock.uptimeMillis()
            ioHandler.post {
                runCatching {
                    ic.beginBatchEdit()
                    ic.finishComposingText()
                    val before = ic.getTextBeforeCursor(FIELD_READ_MAX, 0)?.length ?: 0
                    val after = ic.getTextAfterCursor(FIELD_READ_MAX, 0)?.length ?: 0
                    ic.deleteSurroundingText(before, after)
                    ic.commitText(text, 1)
                    ic.endBatchEdit()
                }
            }
        }

        override fun textBeforeCursorWithin(length: Int, timeoutMs: Long, skipIfSlow: Boolean): String? {
            val ic = currentInputConnection ?: return ""
            return readWithin(timeoutMs, skipIfSlow) { ic.getTextBeforeCursor(length, 0)?.toString().orEmpty() }
        }

        override fun commitImage(fileName: String, mime: String): Boolean {
            val uri = runCatching { imageStore.contentUri(fileName) }.getOrNull() ?: return false
            val ic = currentInputConnection
            val info = currentInputEditorInfo
            if (ic != null && info != null) {
                val supported = EditorInfoCompat.getContentMimeTypes(info)
                if (supported.any { ClipDescription.compareMimeTypes(mime, it) }) {
                    val content = InputContentInfoCompat(uri, ClipDescription("image", arrayOf(mime)), null)
                    val ok = runCatching {
                        InputConnectionCompat.commitContent(
                            ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
                        )
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            // الحقل لا يقبل الصور مباشرة: ننسخها للحافظة ليلصقها المستخدم بتطبيق يدعم ذلك
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                lastCapturedClip = uri.toString()
                cm.setPrimaryClip(ClipData.newUri(contentResolver, "image", uri))
                Toast.makeText(
                    this@SemoKeyboardService,
                    "هذا الحقل لا يقبل الصور مباشرة، نُسخت الصورة للحافظة ويمكنك لصقها بتطبيق يدعم الصور",
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }

        override fun commitFile(fileName: String, mime: String, label: String): Boolean {
            val uri = runCatching { fileStore.contentUri(fileName) }.getOrNull() ?: return false
            val ic = currentInputConnection
            val info = currentInputEditorInfo
            if (ic != null && info != null) {
                val supported = EditorInfoCompat.getContentMimeTypes(info)
                if (supported.any { ClipDescription.compareMimeTypes(mime, it) }) {
                    val content = InputContentInfoCompat(uri, ClipDescription(label, arrayOf(mime)), null)
                    val ok = runCatching {
                        InputConnectionCompat.commitContent(
                            ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
                        )
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            copyFileToClipboard(fileName, mime, label)
            Toast.makeText(this@SemoKeyboardService, getString(com.semo.keyboard.R.string.clip_file_copied), Toast.LENGTH_LONG).show()
            return false
        }

        override fun copyFileToClipboard(fileName: String, mime: String, label: String) {
            runCatching {
                val uri = fileStore.contentUri(fileName)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                lastCapturedClip = uri.toString()
                val description = ClipDescription(label, arrayOf(mime))
                cm.setPrimaryClip(ClipData(description, ClipData.Item(uri)))
            }
        }

        override fun copyLargeTextToClipboard(text: String) {
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                lastCapturedClip = clipKey(text)
                val uri = if (text.length > LARGE_COPY_CHARS) fileStore.shareTextFile(text) else null
                if (uri != null) {
                    cm.setPrimaryClip(ClipData(ClipDescription("text", arrayOf("text/plain")), ClipData.Item(uri)))
                } else {
                    cm.setPrimaryClip(ClipData.newPlainText("text", text))
                }
            }
        }

        override fun cancelPaste() {
            pasteCancelled = true
        }

        override fun copyToClipboard(text: String) {
            runCatching {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                lastCapturedClip = clipKey(text)
                cm.setPrimaryClip(ClipData.newPlainText("text", text))
            }
        }

        override fun copyImageToClipboard(fileName: String, mime: String) {
            runCatching {
                val uri = imageStore.contentUri(fileName)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                lastCapturedClip = uri.toString()
                cm.setPrimaryClip(ClipData.newUri(contentResolver, "image", uri))
            }
        }

        override fun keyFeedback(sound: Boolean, haptic: Boolean, kind: KeyFeedback, volume: Float) {
            val now = SystemClock.uptimeMillis()
            lastTypeAt = now
            if (!sound && !haptic) return
            if (kind == KeyFeedback.DELETE) {
                if (now - lastDeleteFeedbackAt < DELETE_FEEDBACK_GAP_MS) return
                lastDeleteFeedbackAt = now
            }
            if (haptic) requestHaptic(kind)
            if (sound) feedbackHandler.post { runCatching { clickPlayer?.play(kind, volume) } }
        }
    }

    private val pasteMutex = Mutex()

    private suspend fun commitChunked(ic: android.view.inputmethod.InputConnection, text: String) {
        pasteCancelled = false
        var start = 0
        var failed = false
        var chunkSize = if (text.length > COMMIT_BIG_THRESHOLD) COMMIT_CHUNK_BIG else COMMIT_CHUNK
        val showProgress = text.length >= PASTE_PROGRESS_MIN
        try {
            ic.beginBatchEdit()
            while (start < text.length) {
                if (currentInputConnection !== ic || pasteCancelled) break
                var end = minOf(start + chunkSize, text.length)
                if (end < text.length && text[end - 1].isHighSurrogate()) end--
                val began = SystemClock.uptimeMillis()
                if (!ic.commitText(text.substring(start, end), 1)) {
                    failed = true
                    break
                }
                val took = SystemClock.uptimeMillis() - began
                start = end
                chunkSize = when {
                    took < PASTE_TARGET_MS / 2 -> (chunkSize * 2).coerceAtMost(PASTE_MAX_CHUNK)
                    took > PASTE_TARGET_MS * 2 -> (chunkSize / 2).coerceAtLeast(PASTE_MIN_CHUNK)
                    else -> chunkSize
                }
                if (showProgress && ::viewModel.isInitialized) viewModel.onPasteProgress(start.toFloat() / text.length)
                if (start < text.length) yield()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            runCatching { ic.endBatchEdit() }
            if (showProgress && ::viewModel.isInitialized) viewModel.onPasteProgress(null)
        }
        if (failed && currentInputConnection === ic && start < text.length) pasteViaClipboard(ic, text.substring(start))
    }

    /** احتياط للنص الطويل جدًا: لو رفض التطبيق الدفعات نضعه بالحافظة ونطلب منه اللصق الأصلي */
    private fun pasteViaClipboard(ic: android.view.inputmethod.InputConnection, text: String) {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            lastCapturedClip = clipKey(text)
            lastClipTs = 0L
            cm.setPrimaryClip(ClipData.newPlainText("code", text))
            ic.performContextMenuAction(android.R.id.paste)
        }
    }

    /** ينتقل للوحة صوتية مفعّلة بالجهاز (نوع فرعي voice)، وإلا يعرض تنبيهًا */
    @Suppress("DEPRECATION")
    private fun switchToVoiceInput() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        var targetId: String? = null
        var targetSubtype: InputMethodSubtype? = null
        runCatching {
            for (info in imm?.enabledInputMethodList.orEmpty()) {
                for (i in 0 until info.subtypeCount) {
                    val subtype = info.getSubtypeAt(i)
                    if (subtype.mode.equals("voice", ignoreCase = true)) {
                        targetId = info.id
                        targetSubtype = subtype
                        break
                    }
                }
                if (targetId != null) break
            }
        }
        val id = targetId
        val switched = id != null && runCatching {
            val subtype = targetSubtype
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && subtype != null) {
                switchInputMethod(id, subtype)
            } else {
                switchInputMethod(id)
            }
        }.isSuccess
        if (!switched) {
            Toast.makeText(this, "ما في إدخال صوتي مفعّل بالجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrateTick(kind: KeyFeedback) {
        val v = vibrator ?: return
        if (!hasVibrator) return
        val effect = hapticEffects[kind] ?: hapticEffects[if (kind == KeyFeedback.RETURN) KeyFeedback.MODIFIER else KeyFeedback.STANDARD] ?: return
        v.vibrate(effect)
    }
}
