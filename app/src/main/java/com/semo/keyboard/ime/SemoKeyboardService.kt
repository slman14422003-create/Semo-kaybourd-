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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** صوت ضغط شبيه بآيفون (مُولَّد برمجيًا) */
    private var clickPlayer: KeyClickPlayer? = null

    /** الصوت والاهتزاز على خيط خلفي كي لا يؤخّرا رسم الضغطة التالية على الخيط الرئيسي */
    private val feedbackThread = HandlerThread("semo-feedback").also { it.start() }
    private val feedbackHandler = Handler(feedbackThread.looper)

    /** الاهتزاز بخيط مستقل عن الصوت: توقّف أحدهما (AudioTrack) لا يؤخّر الآخر */
    private val hapticThread = HandlerThread("semo-haptic").also { it.start() }
    private val hapticHandler = Handler(hapticThread.looper)

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

    /**
     * تأثيرات هابتك جاهزة: ضربة قصيرة حادّة (مثل Taptic Engine بآيفون) بدل اهتزاز عام طويل "طنّان".
     * من أندرويد 10 نستعمل التأثيرات المعرّفة بالنظام (TICK/CLICK) لأن محرك الاهتزاز مضبوط لها بكل جهاز،
     * وقبله نقرة قصيرة جدًا بشدة متدرجة.
     */
    private val hapticEffects: Map<KeyFeedback, VibrationEffect> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val tick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            val heavy = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            mapOf(
                KeyFeedback.STANDARD to tick,
                KeyFeedback.MODIFIER to tick,
                KeyFeedback.SELECTION to tick,
                KeyFeedback.DELETE to click,
                KeyFeedback.LONG_PRESS to heavy
            )
        } else {
            val amp = vibrator?.hasAmplitudeControl() == true
            fun shot(ms: Long, strength: Int): VibrationEffect =
                VibrationEffect.createOneShot(ms, if (amp) strength else VibrationEffect.DEFAULT_AMPLITUDE)
            mapOf(
                KeyFeedback.STANDARD to shot(8, 70),
                KeyFeedback.MODIFIER to shot(9, 90),
                KeyFeedback.SELECTION to shot(6, 50),
                KeyFeedback.DELETE to shot(10, 120),
                KeyFeedback.LONG_PRESS to shot(16, 180)
            )
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cursorMovedTask = Runnable {
        if (::viewModel.isInitialized) {
            val inputType = currentInputEditorInfo?.inputType ?: 0
            ioHandler.post {
                val capitalize = shouldCapitalize(inputType)
                mainHandler.post {
                    if (::viewModel.isInitialized) viewModel.onCursorMoved(capitalize)
                }
            }
        }
    }

    /** حقل كلمة مرور: لا نسجّل الحافظة ولا نتعلّم كلمات */
    private var passwordField = false
    private var lastCapturedClip: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        clickPlayer = KeyClickPlayer()
        val settingsRepository = SettingsRepository(applicationContext)
        clipboardRepository = ClipboardRepository(applicationContext)
        val learnedRepository = LearnedWordsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            KeyboardViewModelFactory(settingsRepository, clipboardRepository, learnedRepository, bridge)
        )[KeyboardViewModel::class.java]

        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipListener)

        // القاموس الكبير (لو نُزِّل) يُحمَّل بخيط خلفي كي لا يؤخّر ظهور اللوحة
        ioHandler.post { runCatching { DictionaryManager.loadAll(applicationContext) } }
    }

    /** يحفظ آخر نص منسوخ بسجل الحافظة (إلا بحقول كلمات المرور أو لو علّم التطبيق النص كحساس) */
    private fun captureClipboard() {
        if (passwordField || !viewModel.uiState.value.clipboardEnabled) return
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            if (Build.VERSION.SDK_INT >= 33) {
                val sensitive = clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
                if (sensitive) return
            }
            val item = clip.getItemAt(0)
            val desc = clip.description
            val imageMime = (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }.firstOrNull { it.startsWith("image/") }
            val imageUri = item.uri
            if (imageMime != null && imageUri != null) {
                // صورة (لقطة شاشة مثلًا): ننسخها لتخزين التطبيق لأن رابط الحافظة مؤقت
                val id = imageUri.toString()
                if (id == lastCapturedClip) return
                lastCapturedClip = id
                serviceScope.launch {
                    val name = withContext(Dispatchers.IO) { imageStore.saveFromUri(imageUri, imageMime) } ?: return@launch
                    val key = clipImageKey(name, imageMime)
                    clipboardRepository.add(key)
                    viewModel.onClipboardCopied(key)
                }
                return
            }
            val text = item.coerceToText(this)?.toString().orEmpty()
            if (text.isBlank() || text == lastCapturedClip) return
            lastCapturedClip = text
            serviceScope.launch { clipboardRepository.add(text) }
            viewModel.onClipboardCopied(text)
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
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> KeyboardPage.SYMBOLS_1
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
        viewModel.onStartInput(page, shouldCapitalize(inputType), enterKindFor(info), !noSuggestions, fieldKind)
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
        mainHandler.postDelayed(cursorMovedTask, 40)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        KeyboardStatusHelper.setImeVisible(this, true)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        KeyboardStatusHelper.setImeVisible(this, false)
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
        hapticThread.quitSafely()
        ioThread.quitSafely()
        KeyboardStatusHelper.setImeVisible(this, false)
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    private fun shouldCapitalize(inputType: Int): Boolean =
        inputType != 0 && (currentInputConnection?.getCursorCapsMode(inputType) ?: 0) != 0

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
            runCatching { currentInputConnection?.commitText(text, 1) }
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
            runCatching { currentInputConnection?.getTextBeforeCursor(length, 0)?.toString() }.getOrNull().orEmpty()

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
            if (!sound && !haptic) return
            if (haptic) hapticHandler.post { runCatching { vibrateTick(kind) } }
            if (sound) feedbackHandler.post { runCatching { clickPlayer?.play(kind, volume) } }
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
