package com.ios26.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ios26.keyboard.data.SettingsRepository
import com.ios26.keyboard.ui.keyboard.KeyboardScreen
import com.ios26.keyboard.ui.keyboard.KeyboardViewModel

/**
 * خدمة إدخال قياسية على نمط Android IME؛ تستضيف واجهة Compose (KeyboardScreen)
 * وتترجم أوامرها إلى استدعاءات فعلية على InputConnection الخاص بالحقل النشط حاليًا.
 *
 * ندوّر يدويًا حلقة حياة (Lifecycle/ViewModelStore/SavedState) لأن IME ليست
 * Activity أو Fragment، وهذا ضروري لتشغيل ComposeView خارج تلك السياقات.
 *
 * ملاحظة مهمة: نستخدم DisposeOnLifecycleDestroyed(this) بدل DisposeOnDetachedFromWindow.
 * النظام يفصل ويعيد إرفاق نفس الـ View بشكل متكرر أثناء إخفاء/إظهار اللوحة بدون تدمير
 * الخدمة، وDisposeOnDetachedFromWindow كان يهدم الـ Composition عند أول إخفاء، فتتحول
 * أي محاولة إعادة استخدام لاحقة لها إلى استثناء (composition تم التخلص منها) ويصير كراش
 * فوري بمجرد اختيار اللوحة من النظام. الربط الآن بدورة حياة الخدمة نفسها يحل المشكلة جذريًا.
 */
class IOSKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var settingsRepository: SettingsRepository
    private var viewModel: KeyboardViewModel? = null

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        settingsRepository = SettingsRepository(applicationContext)
    }

    override fun onCreateInputView(): View {
        val vm = KeyboardViewModel(
            settingsRepository = settingsRepository,
            onCommitText = { text -> safeInputConnection { commitText(text, 1) } },
            onDeleteBackward = { safeInputConnection { deleteSurroundingText(1, 0) } },
            onCommitEnter = { sendEnter() },
            onSwitchInputMethod = { switchToNextInputMethod(false) }
        )
        viewModel = vm

        return ComposeView(this).apply {
            // ننسّق التخلص من الـ Composition مع دورة حياة الخدمة نفسها، لا مع إرفاق/فصل الـ View
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@IOSKeyboardService))
            setViewTreeLifecycleOwner(this@IOSKeyboardService)
            setViewTreeViewModelStoreOwner(this@IOSKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@IOSKeyboardService)
            setContent { KeyboardScreen(viewModel = vm) }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // نرجع لـ STARTED فقط (وليس أدنى)، لأن الـ View ما زال قد يكون مرئيًا لفترة قصيرة
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        viewModel = null
    }

    private fun sendEnter() {
        safeInputConnection {
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    /**
     * ينفّذ عملية على InputConnection الحالي بأمان: قد يكون null أو غير صالح (مثلًا الحقل
     * فقد التركيز لحظة الضغط)، فنتجنب أي استثناء يوقف الخدمة بالكامل بدل تعطّل ضغطة واحدة فقط.
     */
    private inline fun safeInputConnection(action: android.view.inputmethod.InputConnection.() -> Unit) {
        val ic = currentInputConnection ?: return
        runCatching { ic.action() }
    }
}
