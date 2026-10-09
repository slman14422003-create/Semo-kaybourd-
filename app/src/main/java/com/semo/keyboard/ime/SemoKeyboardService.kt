package com.semo.keyboard.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.ui.keyboard.InputBridge
import com.semo.keyboard.ui.keyboard.KeyboardScreen
import com.semo.keyboard.ui.keyboard.KeyboardViewModel
import com.semo.keyboard.ui.keyboard.KeyboardViewModelFactory

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

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val repository = SettingsRepository(applicationContext)
        viewModel = ViewModelProvider(this, KeyboardViewModelFactory(repository, bridge))[KeyboardViewModel::class.java]
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setContent { KeyboardScreen(viewModel = viewModel) }
        }
        installViewTreeOwners(composeView)
        window?.window?.decorView?.let { installViewTreeOwners(it) }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return composeView
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
        viewModel.onStartInput(page, shouldCapitalize(inputType), enterKindFor(info))
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        viewModel.onCursorMoved(shouldCapitalize(currentInputEditorInfo?.inputType ?: 0))
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
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
                // لو في نص محدد نحذفه كله، وإلا نرسل DEL حقيقي (يتعامل صح مع الإيموجي وأزواج الـ surrogate)
                if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
                else sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
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
            requestHideSelf(0)
        }

        override fun textBeforeCursor(length: Int): String =
            runCatching { currentInputConnection?.getTextBeforeCursor(length, 0)?.toString() }.getOrNull().orEmpty()

        override fun keyFeedback(sound: Boolean, haptic: Boolean) {
            if (sound) runCatching {
                (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
            }
            if (haptic) runCatching { vibrateTick() }
        }
    }

    private fun vibrateTick() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
