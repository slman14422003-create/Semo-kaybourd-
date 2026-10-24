package com.semo.keyboard.ui.keyboard

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Soft blurred backdrop for the glass theme. An Android keyboard window has nothing of the app under it
 * (the app is resized above the keyboard), so the frosted look is built from a tiny downscaled copy of the
 * wallpaper bottom strip, which upscales into a smooth blur at near-zero cost, or from a colour aurora.
 */
private object GlassBackdropCache {
    @Volatile var dark: ImageBitmap? = null
    @Volatile var light: ImageBitmap? = null
}

private fun aurora(dark: Boolean): Bitmap {
    val w = 48
    val h = 24
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(if (dark) 0xFF0A0C14.toInt() else 0xFFEEF0F7.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun blob(x: Float, y: Float, r: Float, color: Int) {
        p.shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
    val a = if (dark) 0xCC else 0x99
    blob(w * 0.12f, h * 0.15f, w * 0.55f, (a shl 24) or 0xFF2D87)
    blob(w * 0.90f, h * 0.10f, w * 0.50f, (a shl 24) or 0x5B3BFF)
    blob(w * 0.55f, h * 1.00f, w * 0.65f, (a shl 24) or 0x1E90FF)
    return bmp
}

private fun wallpaperStrip(context: Context): Bitmap? = runCatching {
    val d = WallpaperManager.getInstance(context).peekDrawable() ?: return null
    val dw = d.intrinsicWidth.coerceAtLeast(1)
    val dh = d.intrinsicHeight.coerceAtLeast(1)
    val w = 48
    val h = (w * dh / dw.toFloat()).toInt().coerceAtLeast(24)
    val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, w, h)
    d.draw(Canvas(full))
    val cropH = (h * 0.4f).toInt().coerceAtLeast(12)
    Bitmap.createBitmap(full, 0, h - cropH, w, cropH)
}.getOrNull()

@Composable
fun rememberGlassBackdrop(enabled: Boolean, dark: Boolean): State<ImageBitmap?> {
    val context = LocalContext.current.applicationContext
    return produceState<ImageBitmap?>(initialValue = null, enabled, dark) {
        if (!enabled) {
            value = null
            return@produceState
        }
        val cached = if (dark) GlassBackdropCache.dark else GlassBackdropCache.light
        if (cached != null) value = cached
        val built = withContext(Dispatchers.Default) {
            (wallpaperStrip(context) ?: aurora(dark)).asImageBitmap()
        }
        if (dark) GlassBackdropCache.dark = built else GlassBackdropCache.light = built
        value = built
    }
}
