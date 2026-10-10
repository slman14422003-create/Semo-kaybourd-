package com.semo.keyboard.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * صور الحافظة (لقطات الشاشة وغيرها): رابط الصورة بالحافظة مؤقت وتفقد صلاحيته، فنخزّن نسخة بتخزين التطبيق
 * (مشفّر بقفل الجهاز) ونعرضها بسجل الحافظة ونلصقها لاحقًا عبر FileProvider.
 * اسم الملف = بصمة محتواه، فالصورة نفسها لا تتكرر.
 */
class ClipImageStore(context: Context) {

    private val app = context.applicationContext
    private val dir: File get() = File(app.filesDir, DIR_NAME)

    fun file(name: String): File = File(dir, name)

    /** رابط يستطيع التطبيق الهدف قراءته (مع منح الصلاحية عند اللصق) */
    fun contentUri(name: String): Uri =
        FileProvider.getUriForFile(app, app.packageName + AUTHORITY_SUFFIX, file(name))

    /** ينسخ الصورة من رابط الحافظة المؤقت. يرجع اسم الملف أو null لو فشل أو كانت كبيرة جدًا */
    fun saveFromUri(uri: Uri, mime: String): String? {
        if (!app.isUserStorageUnlocked()) return null
        return runCatching {
            dir.mkdirs()
            dir.listFiles()?.forEach { if (it.name.startsWith(TMP_PREFIX)) it.delete() }
            val tmp = File(dir, TMP_PREFIX + System.nanoTime())
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            val input = app.contentResolver.openInputStream(uri) ?: return null
            input.use { stream ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            tmp.delete()
                            return null
                        }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) {
                tmp.delete()
                return null
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }.take(20)
            val name = hash + "." + extensionFor(mime)
            val target = file(name)
            if (target.exists()) {
                tmp.delete()
            } else if (!tmp.renameTo(target)) {
                tmp.delete()
                return null
            }
            name
        }.getOrNull()
    }

    /** مصغّرة الصورة للعرض بالقائمة (تُفك بعينة مصغّرة كي لا تستهلك الذاكرة، وتُخزَّن مؤقتًا) */
    fun thumbnail(name: String, maxPx: Int = 360): Bitmap? {
        cache.get(name)?.let { return it }
        val f = file(name)
        if (!f.exists()) return null
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
        if (bitmap != null) cache.put(name, bitmap)
        return bitmap
    }

    fun delete(name: String) {
        cache.remove(name)
        runCatching { file(name).delete() }
    }

    fun deleteAll() {
        cache.evictAll()
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun extensionFor(mime: String): String = when {
        mime.contains("png", ignoreCase = true) -> "png"
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
        mime.contains("webp", ignoreCase = true) -> "webp"
        mime.contains("gif", ignoreCase = true) -> "gif"
        else -> "img"
    }

    companion object {
        const val DIR_NAME = "clip_images"
        const val AUTHORITY_SUFFIX = ".clipfiles"
        private const val TMP_PREFIX = "tmp_"
        private const val MAX_BYTES = 12L * 1024 * 1024

        private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }
}
