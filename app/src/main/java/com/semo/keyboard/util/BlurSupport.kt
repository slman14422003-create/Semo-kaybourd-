package com.semo.keyboard.util

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.function.Consumer

object BlurSupport {
    var enabled by mutableStateOf(false)
        private set

    private var listener: Consumer<Boolean>? = null
    private var manager: WindowManager? = null

    fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun attach(context: Context) {
        if (!available() || listener != null) return
        val wm = runCatching { context.getSystemService(WindowManager::class.java) }.getOrNull() ?: return
        val l = Consumer<Boolean> { enabled = it }
        runCatching {
            enabled = wm.isCrossWindowBlurEnabled
            wm.addCrossWindowBlurEnabledListener(context.mainExecutor, l)
        }.onSuccess {
            listener = l
            manager = wm
        }
    }

    fun detach() {
        val l = listener ?: return
        runCatching { manager?.removeCrossWindowBlurEnabledListener(l) }
        listener = null
        manager = null
    }
}
