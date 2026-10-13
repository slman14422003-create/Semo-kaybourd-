package com.semo.keyboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
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
import com.semo.keyboard.data.ClipImageStore
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.ShortcutsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.clipImageKey
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.FieldKind
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.ui.keyboard.InputBridge
import com.semo.keyboard.ui.keyboard.KeyFeedback
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** أقصى عدد أحرف بدفعة لصق واحدة (النص الطويل يُلصق على دفعات كي لا يتجاوز حدّ حجم رسالة Binder) */
private const val COMMIT_CHUNK = 4_000
private const val SLOW_BACKOFF_MS = 600L
private const val EXPLICIT_READ_MS = 250L
private const val FIELD_READ_MS = 1500L
private const val FIELD_READ_MAX = 4500
private const val START_READ_MS = 100L
private const val DELETE_FEEDBACK_GAP_MS = 35L
private const val POLL_IDLE_MS = 1200L

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

    /** هل يوجد نص محدد؟ نحدّثه من onUpdateSelection بدل سؤال التطبيق (getSelectedText) عند كل حذف */
    @Volatile private var hasSelection = false

    private val imageStore by lazy { ClipImageStore(applicationContext) }
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
                if (pollTick % 4 == 0) captureScreenshot()
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
        val cutoff = System.currentTimeMillis() / 1000 - 180
        val like = if (Build.VERSION.SDK_INT >= 29)
            "(${android.provider.MediaStore.Images.Media.DISPLAY_NAME} LIKE '%creenshot%' OR ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE '%creenshot%')"
        else "${android.provider.MediaStore.Images.Media.DATA} LIKE '%creenshot%'"
        val sel = "${android.provider.MediaStore.Images.Media.DATE_ADDED} >= ? AND $like"
        contentResolver.query(
            media,
            arrayOf(android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.MIME_TYPE),
            sel, arrayOf(cutoff.toString()),
            // LIMIT 1: نريد فقط أحدث صورة، فلا داعي أن يفرز المزوّد ويرجع كل الصفوف المطابقة
            "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val mime = c.getString(1) ?: "image/png"
                return Shot(id, android.content.ContentUris.withAppendedId(media, id), mime)
            }
        }
        return null
    }

    /** لقطات الشاشة التي لا تُنسخ للحافظة تلقائيًا: نلتقط أحدث لقطة (آخر 3 دقائق) لو مُنح إذن الصور */
    private fun captureScreenshot() {
        if (passwordField || !viewModel.uiState.value.clipboardEnabled || !hasMediaPermission()) return
        if (shotJob?.isActive == true) return
        shotJob = serviceScope.launch {
            val shot = withContext(Dispatchers.IO) { runCatching { latestScreenshot() }.getOrNull() } ?: return@launch
            if (shot.id == lastShotId) return@launch
            lastShotId = shot.id
            val name = withContext(Dispatchers.IO) { runCatching { imageStore.saveFromUri(shot.uri, shot.mime) }.getOrNull() } ?: return@launch
            val stored = clipboardRepository.add(clipImageKey(name, shot.mime)) ?: return@launch
            viewModel.onClipboardCopied(stored)
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard(force = true) }

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        clickPlayer = KeyClickPlayer(applicationContext)
        startCacheCycle()
        prewarmAssets()
        val settingsRepository = SettingsRepository(applicationContext)
        clipboardRepository = ClipboardRepository(applicationContext)
        val learnedRepository = LearnedWordsRepository(applicationContext)
        val shortcutsRepository = ShortcutsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            KeyboardViewModelFactory(settingsRepository, clipboardRepository, learnedRepository, shortcutsRepository, bridge)
        )[KeyboardViewModel::class.java]

        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipListener)

        // القاموس الكبير (لو نُزِّل) يُحمَّل بخيط خلفي كي لا يؤخّر ظهور اللوحة
        ioHandler.post { runCatching { DictionaryManager.loadAll(applicationContext) } }
        hapticHandler.post { runCatching { if (hasVibrator) hapticEffects.size } }
    }

    private class ClipSnap(val text: String?, val uri: android.net.Uri?, val imageMime: String?)

    private fun clipKey(text: String): String = "t${text.length}:${text.hashCode()}"

    /** يقرأ الحافظة بخيط خلفي (النص الطويل قد يتأخر) ويرجع null لو فاضية أو حساسة */
    private fun readClip(): ClipSnap? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        if (Build.VERSION.SDK_INT >= 33) {
            val sensitive = clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            if (sensitive) return null
        }
        val desc = clip.description
        val items = (0 until clip.itemCount).map { clip.getItemAt(it) }
        val descImageMime = (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }.firstOrNull { it.startsWith("image/") }
        val plain = items.firstNotNullOfOrNull { runCatching { it.text?.toString() }.getOrNull()?.takeIf { s -> s.isNotBlank() } }
        val html = if (plain == null) {
            items.firstNotNullOfOrNull { runCatching { it.htmlText }.getOrNull()?.takeIf { s -> s.isNotBlank() } }
                ?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
                ?.takeIf { it.isNotBlank() }
        } else null
        val coerced = if (plain == null && html == null) {
            items.firstNotNullOfOrNull { runCatching { it.coerceToText(this)?.toString() }.getOrNull()?.takeIf { s -> s.isNotBlank() } }
        } else null
        val uri = items.firstNotNullOfOrNull { item -> item.uri?.takeIf { it.scheme == "content" } }
        val plainLooksLikeUri = plain == null || plain.trim().let {
            it.startsWith("content:") || it.startsWith("file:") || it.startsWith("/") || it == uri?.toString()
        }
        val mime = if (uri != null && plainLooksLikeUri) resolveImageMime(descImageMime, uri) else null
        // بعض التطبيقات تنسخ الروابط كـ ClipData.Item فيه uri فقط بلا نص (text/uri-list)،
        // فلو ما لقينا نصًا من أي مصدر سابق نأخذ الرابط من أول uri غير content (http/https/mailto...)
        val linkText = if (plain == null && html == null && coerced == null) {
            items.firstNotNullOfOrNull { item -> item.uri?.takeIf { it.scheme != null && it.scheme != "content" }?.toString() }
        } else null
        return ClipSnap(plain ?: html ?: coerced ?: linkText, uri, mime)
    }

    /**
     * يحفظ آخر شيء نُسخ بسجل الحافظة (إلا بحقول كلمات المرور أو لو علّم التطبيق النص كحساس).
     * القراءة والحفظ بخيط خلفي مع إعادة محاولة (الحافظة قد لا تكون جاهزة لحظة الإشعار)، والنص الطويل يُحفظ كاملًا بملف.
     */
    private fun captureClipboard(force: Boolean = false) {
        if (passwordField || !viewModel.uiState.value.clipboardEnabled) return
        if (!force && clipJob?.isActive == true) return
        clipJob = serviceScope.launch {
            // استطلاع دوري: نسأل عن بصمة زمن الحافظة فقط (خفيف) ولا نسحب النص الكامل عبر Binder كل مرة
            val ts = withContext(Dispatchers.IO) {
                runCatching {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClipDescription?.timestamp
                }.getOrNull() ?: 0L
            }
            if (!force && ts != 0L && ts == lastClipTs) return@launch
            // نحاول القراءة فورًا أولًا (الحافظة جاهزة غالبًا لحظة الإشعار)، ونؤجّل الانتظار
            // لمحاولات لاحقة فقط لو فشلت الأولى، بدل انتظار ثابت قبل كل قراءة
            var snap: ClipSnap? = null
            val delays = if (force) longArrayOf(0L, 60L, 120L, 250L) else longArrayOf(0L, 60L, 150L, 300L)
            for (attempt in delays.indices) {
                if (delays[attempt] > 0L) delay(delays[attempt])
                snap = withContext(Dispatchers.IO) { runCatching { readClip() }.getOrNull() }
                if (snap != null && (snap.text != null || snap.imageMime != null)) break
            }
            if (ts != 0L && snap != null) lastClipTs = ts
            val s = snap ?: return@launch
            if (s.imageMime != null && s.uri != null) {
                val id = s.uri.toString()
                if (!force && id == lastCapturedClip) return@launch
                lastCapturedClip = id
                val name = withContext(Dispatchers.IO) { runCatching { imageStore.saveFromUri(s.uri, s.imageMime) }.getOrNull() } ?: return@launch
                val stored = clipboardRepository.add(clipImageKey(name, s.imageMime)) ?: return@launch
                viewModel.onClipboardCopied(stored)
                return@launch
            }
            val text = s.text ?: return@launch
            val key = withContext(Dispatchers.Default) { clipKey(text) }
            if (!force && key == lastCapturedClip) return@launch
            lastCapturedClip = key
            var stored = clipboardRepository.add(text)
            if (stored == null) {
                delay(300)
                stored = clipboardRepository.add(text)
            }
            if (stored != null) viewModel.onClipboardCopied(stored)
        }
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
        }.getOrNull()
    }

    private fun startCacheCycle() {
        serviceScope.launch {
            while (isActive) {
                val wait = com.semo.keyboard.data.CacheCleaner.millisUntilNext(applicationContext)
                if (wait > 0L) delay(wait)
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
            setContent { KeyboardScreen(viewModel = viewModel, onChrome = ::applyChrome) }
        }
        installViewTreeOwners(composeView)
        window?.window?.decorView?.let { installViewTreeOwners(it) }
        // نافذة شفافة كي تظهر زوايا اللوحة المدورة بنمط iOS 26
        window?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return composeView
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
                    variation == InputType.TYPE_TEXT_VARIATION_URI ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                )) ||
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        val fieldKind = when {
            isTextClass && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) -> FieldKind.EMAIL
            isTextClass && variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            else -> FieldKind.TEXT
        }
        KeyboardStatusHelper.markImeSeen(this)
        hasSelection = (info?.initialSelStart ?: 0) != (info?.initialSelEnd ?: 0)
        val startIc = currentInputConnection
        val startCaps = if (inputType == 0) false else readWithin(START_READ_MS, true) { shouldCapitalize(inputType, startIc) }
        viewModel.onStartInput(page, startCaps == true, enterKindFor(info), !noSuggestions, fieldKind)
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
        // نجمع تحديثات المؤشر المتتالية (تصل مع كل حرف) بمهمة واحدة، لأن كل واحدة تكلّف استدعاءات للتطبيق
        mainHandler.removeCallbacks(cursorMovedTask)
        mainHandler.postDelayed(cursorMovedTask, 70)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        KeyboardStatusHelper.setImeVisible(this, true)
        captureClipboard()
        mainHandler.removeCallbacks(pollTask)
        mainHandler.postDelayed(pollTask, 600)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (::viewModel.isInitialized) viewModel.flushLearning()
        mainHandler.removeCallbacks(pollTask)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        KeyboardStatusHelper.setImeVisible(this, false)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // لو ما صفّرنا هاد العلم بعد الخروج من حقل كلمة مرور، بيضل عالق true ويمنع التقاط
        // النسخ من أي تطبيق ثاني لاحقًا (حتى لو مو حقل كلمة مرور إطلاقًا) إلى حين فتح حقل جديد.
        passwordField = false
    }

    /** صور الإيموجي الجاهزة تأخذ عشرات الميغابايت: نحرّرها لما يطلب النظام ذاكرة (اللوحة مخفية) */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.semo.keyboard.ui.keyboard.EmojiBitmapCache.clear()
        }
    }

    override fun onDestroy() {
        runCatching {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(clipListener)
        }
        serviceScope.cancel()
        mainHandler.removeCallbacks(cursorMovedTask)
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
            if (text.length <= COMMIT_CHUNK) {
                runCatching { ic.commitText(text, 1) }
                return
            }
            // النص الطويل (سجلات، أكواد، مقالات): الاتصال بالتطبيق الهدف عبر Binder وحجم الرسالة الواحدة محدود (~1MB)،
            // فنلصقه على دفعات داخل عملية تحرير واحدة (تراجع واحد) مع تسليم الخيط للواجهة بين كل دفعة كي لا تتجمّد
            serviceScope.launch {
                var failed = false
                var stale = false
                runCatching {
                    ic.beginBatchEdit()
                    var start = 0
                    while (start < text.length) {
                        if (currentInputConnection !== ic) { stale = true; break }
                        var end = minOf(start + COMMIT_CHUNK, text.length)
                        // لا نقطع زوج surrogate (إيموجي) بين دفعتين
                        if (end < text.length && text[end - 1].isHighSurrogate()) end--
                        if (!ic.commitText(text.substring(start, end), 1)) {
                            failed = true
                            break
                        }
                        start = end
                        yield()
                    }
                    ic.endBatchEdit()
                    if (failed && !stale) pasteViaClipboard(ic, text)
                }
            }
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
            val code = if (delta > 0) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
            runCatching { repeat(abs(delta)) { sendDownUpKeyEvents(code) } }
        }

        override fun moveCursorVertical(lines: Int) {
            if (lines == 0) return
            val code = if (lines > 0) android.view.KeyEvent.KEYCODE_DPAD_DOWN else android.view.KeyEvent.KEYCODE_DPAD_UP
            runCatching { repeat(abs(lines)) { sendDownUpKeyEvents(code) } }
        }

        override fun startVoiceInput() {
            this@SemoKeyboardService.switchToVoiceInput()
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
        val effect = hapticEffects[kind] ?: hapticEffects[KeyFeedback.STANDARD] ?: return
        v.vibrate(effect)
    }
}
