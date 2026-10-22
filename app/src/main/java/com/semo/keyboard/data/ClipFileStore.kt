package com.semo.keyboard.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

class ClipFileStore(context: Context) {

    private val app = context.applicationContext
    private val dir: File get() = File(app.filesDir, DIR_NAME)
    private val shareDir: File get() = File(app.filesDir, SHARE_DIR_NAME)

    sealed class Result {
        class Saved(val name: String, val size: Long, val label: String, val mime: String) : Result()
        object TooLarge : Result()
        object NoSpace : Result()
        object Failed : Result()
    }

    fun file(name: String): File = File(dir, name)

    fun contentUri(name: String): Uri =
        FileProvider.getUriForFile(app, app.packageName + ClipImageStore.AUTHORITY_SUFFIX, file(name))

    fun sizeOf(name: String): Long = runCatching { file(name).length() }.getOrDefault(0L)

    fun displayNameOf(uri: Uri): String? = queryMeta(app.contentResolver, uri).first

    fun saveFromUri(uri: Uri, declaredMime: String?, maxBytes: Long): Result {
        if (!app.isUserStorageUnlocked()) return Result.Failed
        return runCatching {
            val resolver = app.contentResolver
            val meta = queryMeta(resolver, uri)
            val declaredSize = meta.second
            if (declaredSize != null && declaredSize > maxBytes) return Result.TooLarge
            dir.mkdirs()
            val staleBefore = System.currentTimeMillis() - TMP_STALE_MS
            dir.listFiles()?.forEach { if (it.name.startsWith(TMP_PREFIX) && it.lastModified() < staleBefore) it.delete() }
            val needed = (declaredSize ?: 0L) + SPACE_MARGIN
            if (dir.usableSpace < needed) return Result.NoSpace
            val tmp = File(dir, TMP_PREFIX + System.nanoTime())
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            val input = resolver.openInputStream(uri) ?: return Result.Failed
            input.use { stream ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) {
                            tmp.delete()
                            return Result.TooLarge
                        }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) {
                tmp.delete()
                return Result.Failed
            }
            val label = meta.first?.takeIf { it.isNotBlank() } ?: "file"
            val ext = extensionOf(label)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }.take(20)
            val name = if (ext.isEmpty()) hash else "$hash.$ext"
            val target = file(name)
            if (target.exists()) {
                tmp.delete()
                target.setLastModified(System.currentTimeMillis())
            } else if (!tmp.renameTo(target)) {
                tmp.delete()
                return Result.Failed
            }
            Result.Saved(name, total, label, resolveMime(resolver, uri, declaredMime, ext))
        }.getOrDefault(Result.Failed)
    }

    fun shareTextFile(text: String): Uri? {
        if (!app.isUserStorageUnlocked()) return null
        return runCatching {
            shareDir.mkdirs()
            val bytes = text.toByteArray(Charsets.UTF_8)
            val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(20)
            val target = File(shareDir, "$hash.txt")
            if (!target.exists()) target.writeBytes(bytes) else target.setLastModified(System.currentTimeMillis())
            shareDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(SHARE_KEEP)?.forEach { it.delete() }
            FileProvider.getUriForFile(app, app.packageName + ClipImageStore.AUTHORITY_SUFFIX, target)
        }.getOrNull()
    }

    fun delete(name: String) {
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return
        runCatching { file(name).delete() }
    }

    fun deleteAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        runCatching { shareDir.listFiles()?.forEach { it.delete() } }
    }

    fun retain(names: Set<String>, minAgeMs: Long) {
        val now = System.currentTimeMillis()
        runCatching {
            dir.listFiles()?.forEach { if (it.name !in names && now - it.lastModified() >= minAgeMs) it.delete() }
        }
    }

    private fun queryMeta(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use Pair<String?, Long?>(null, null)
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0 && !c.isNull(nameIdx)) c.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx).takeIf { it > 0 } else null
                Pair(name, size)
            }
        }.getOrNull() ?: Pair(null, null)
    }

    private fun extensionOf(label: String): String {
        val raw = label.substringAfterLast('.', "").lowercase()
        return if (raw.length in 1..8 && raw.all { it.isLetterOrDigit() }) raw else ""
    }

    private fun resolveMime(resolver: ContentResolver, uri: Uri, declared: String?, ext: String): String {
        val provider = runCatching { resolver.getType(uri) }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.endsWith("/*") && it != "application/octet-stream" }
        if (provider != null) return provider
        val fromExt = if (ext.isEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (fromExt != null) return fromExt
        return declared?.takeIf { it.isNotBlank() && !it.endsWith("/*") } ?: "application/octet-stream"
    }

    companion object {
        const val DIR_NAME = "clip_files"
        const val SHARE_DIR_NAME = "clip_share"
        private const val TMP_PREFIX = "tmp_"
        private const val TMP_STALE_MS = 2L * 60L * 1000L
        private const val SPACE_MARGIN = 32L * 1024 * 1024
        private const val SHARE_KEEP = 3

        val TEXT_EXTENSIONS = setOf(
            "txt", "log", "md", "csv", "tsv", "json", "xml", "yml", "yaml", "ini", "conf", "cfg", "properties",
            "kt", "java", "js", "ts", "py", "c", "h", "cpp", "cs", "go", "rs", "rb", "php", "sh", "sql", "html", "css", "srt"
        )

        fun sniffImageMime(resolver: ContentResolver, uri: Uri): String? = runCatching {
            val head = ByteArray(16)
            val read = resolver.openInputStream(uri)?.use { it.read(head) } ?: return null
            if (read < 12) return null
            fun at(i: Int, vararg b: Int) = b.indices.all { head[i + it] == b[it].toByte() }
            when {
                at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
                at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
                at(0, 0x47, 0x49, 0x46, 0x38) -> "image/gif"
                at(0, 0x42, 0x4D) -> "image/bmp"
                at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp"
                at(4, 0x66, 0x74, 0x79, 0x70) -> {
                    val brand = String(head, 8, 4, Charsets.US_ASCII).lowercase()
                    when {
                        brand.startsWith("avi") -> "image/avif"
                        brand.startsWith("hei") || brand.startsWith("hev") || brand == "mif1" || brand == "msf1" -> "image/heic"
                        else -> null
                    }
                }
                else -> null
            }
        }.getOrNull()
    }
}
