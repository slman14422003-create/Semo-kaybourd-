package com.semo.keyboard.ui.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * مخزن صور الإيموجي الجاهزة.
 *
 * سبب تقطيع التمرير بلوحات الإيموجي عمومًا: كل إيموجي جديد يظهر بالشاشة يحتاج أن يفكّ المعالج خط الإيموجي
 * الملوّن (صورة PNG مضمّنة بالخط) ويصغّرها ويرسمها داخل مسار الرسم نفسه، وعند التمرير السريع يظهر عشرات
 * الرموز الجديدة بكل إطار فيتجاوز الإطار زمنه.
 *
 * الحل: نرسم كل إيموجي مرة واحدة على خيط خلفي إلى Bitmap جاهز بحجمه النهائي ونحتفظ به، وعند التمرير نرسم
 * Bitmap فقط (عملية رخيصة جدًا). التحضير المسبق يبدأ من القسم الظاهر ويمتد لبقية الأقسام.
 */
internal object EmojiBitmapCache {

    private const val GLYPH_DP = 28f
    private const val CACHE_BYTES = 44 * 1024 * 1024

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Volatile private var bitmapPx = 0
    @Volatile private var glyphEmPx = 0f

    /** يزيد كلما وصلت صور جديدة، تقرؤه عملية الرسم فتُعاد الرسمة تلقائيًا */
    val version = mutableIntStateOf(0)

    /** حجم رمز الإيموجي بالبكسل (للرسم الاحتياطي لو لم تجهز صورته بعد) */
    val emPx: Float get() = glyphEmPx

    /** يضبط حجم الصور حسب كثافة الشاشة (ويمسح المخزن لو تغيّرت الكثافة) */
    fun configure(density: Float) {
        val em = GLYPH_DP * density
        val px = (em * 1.14f).toInt().coerceAtLeast(24)
        if (px != bitmapPx) {
            cache.evictAll()
            glyphEmPx = em
            bitmapPx = px
        }
    }

    fun get(emoji: String): Bitmap? = cache.get(emoji)

    fun clear() {
        cache.evictAll()
    }

    /** يحضّر الصور بالترتيب المعطى (الأقرب لما يراه المستخدم أولًا) ويتوقف فور إلغاء المهمة */
    suspend fun prefetch(order: List<String>) {
        var added = 0
        for (emoji in order) {
            currentCoroutineContext().ensureActive()
            if (cache.get(emoji) != null) continue
            val bitmap = render(emoji) ?: continue
            cache.put(emoji, bitmap)
            added++
            if (added % 48 == 0) version.intValue++
        }
        if (added > 0) version.intValue++
    }

    private fun render(emoji: String): Bitmap? {
        val size = bitmapPx
        if (size <= 0) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            synchronized(paint) {
                paint.textSize = glyphEmPx
                val width = paint.measureText(emoji)
                val limit = size * 0.98f
                if (width > limit) paint.textSize = glyphEmPx * limit / width
                val fm = paint.fontMetrics
                canvas.drawText(emoji, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
            }
            bitmap
        }.getOrNull()
    }
}
