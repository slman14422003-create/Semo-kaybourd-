package com.semo.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.WindowManager
import java.util.function.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns every window-level blur setting of the keyboard window (API 31+).
 *
 * Why window-level: a RenderEffect can only blur what its own view draws. The app under the keyboard lives in
 * another window, and only the system compositor (SurfaceFlinger) can blur another window's pixels. The
 * compositor is driven by two window attributes, set here: the background blur radius and FLAG_BLUR_BEHIND.
 *
 * Lifecycle: [attach] in onCreate, [update] whenever the theme changes, [detach] in onDestroy.
 */
class GlassWindowController(private val service: InputMethodService) {

    private val _blurEnabled = MutableStateFlow(false)

    /** Live: false when the device, the OEM, battery saver or the user turned cross-window blur off */
    val blurEnabled: StateFlow<Boolean> = _blurEnabled

    private var listener: Consumer<Boolean>? = null
    private var wantGlass = false
    private var radiusPx = 0
    private var behindApplied = false

    fun attach() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wm = service.getSystemService(WindowManager::class.java) ?: return
        _blurEnabled.value = runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
        // Blur can be switched off at runtime (battery saver, developer option, thermal throttling), so listen
        val l = Consumer<Boolean> { on ->
            _blurEnabled.value = on
            apply()
        }
        listener = l
        runCatching { wm.addCrossWindowBlurEnabledListener(service.mainExecutor, l) }
    }

    fun detach() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val l = listener ?: return
        listener = null
        runCatching { service.getSystemService(WindowManager::class.java)?.removeCrossWindowBlurEnabledListener(l) }
    }

    fun update(glass: Boolean, radiusPx: Int) {
        if (glass == wantGlass && radiusPx == this.radiusPx) return
        wantGlass = glass
        this.radiusPx = radiusPx
        apply()
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val w = service.window?.window ?: return
        val on = wantGlass && _blurEnabled.value
        runCatching {
            // Blurs what is behind the window background. The window background must stay translucent
            // (set transparent in onCreateInputView), otherwise there is nothing to see through.
            w.setBackgroundBlurRadius(if (on) radiusPx else 0)
            if (on != behindApplied) {
                behindApplied = on
                val lp = w.attributes
                if (on && USE_BLUR_BEHIND) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    lp.blurBehindRadius = radiusPx
                } else {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    lp.blurBehindRadius = 0
                }
                w.attributes = lp
            }
        }
    }

    private companion object {
        /**
         * FLAG_BLUR_BEHIND blurs everything behind the whole window rectangle, background blur only the area
         * behind the window background. Using both doubles the work for the same look, so only one is on.
         */
        const val USE_BLUR_BEHIND = false
    }
}
