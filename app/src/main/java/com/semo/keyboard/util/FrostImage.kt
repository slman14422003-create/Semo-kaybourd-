package com.semo.keyboard.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** خلفية صورة مموّهة مخصّصة للكيبورد — تعمل على كل إصدارات أندرويد (التمويه عبر StackBlur المأخوذ من Blurry). */
object FrostImage {
    private const val FILE = "blur_bg.jpg"
    private const val STORE_MAX = 1280
    private const val BLUR_WIDTH = 540

    private var cacheKey: String? = null
    private var cacheBitmap: ImageBitmap? = null

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** ينسخ الصورة المختارة بحجم معقول ويحفظها داخل التطبيق. */
    fun importFrom(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.applicationContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= STORE_MAX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
            file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            synchronized(this) { cacheKey = null; cacheBitmap = null }
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        synchronized(this) { cacheKey = null; cacheBitmap = null }
    }

    /** يعيد الصورة بعد التشبّع والتمويه (يُستدعى خارج الخيط الرئيسي). */
    fun render(context: Context, stamp: Long, radius: Int, saturation: Float): ImageBitmap? {
        if (stamp == 0L) return null
        val key = "$stamp:$radius:${(saturation * 100).toInt()}"
        synchronized(this) { if (key == cacheKey) return cacheBitmap }
        val f = file(context)
        if (!f.exists()) return null
        val result = try {
            val src = BitmapFactory.decodeFile(f.path) ?: return null
            val w = minOf(BLUR_WIDTH, src.width)
            val h = (src.height * (w.toFloat() / src.width)).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            if (small !== src) src.recycle()
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            if (saturation != 1f) {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
            }
            Canvas(out).drawBitmap(small, 0f, 0f, paint)
            small.recycle()
            StackBlur.blur(out, radius).asImageBitmap()
        } catch (e: Throwable) {
            null
        }
        synchronized(this) { cacheKey = key; cacheBitmap = result }
        return result
    }
}
