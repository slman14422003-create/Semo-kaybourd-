package com.semo.keyboard.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * النصوص الطويلة بالحافظة (أكواد، مقالات...): ملف نصي لكل عنصر بتخزين التطبيق (مشفّر بقفل الجهاز).
 * اسم الملف = بصمة محتواه، فالنص نفسه لا يتكرر. بهذا يبقى مخزن التفضيلات صغيرًا وسريعًا مهما طال النص.
 */
class ClipTextStore(context: Context) {

    private val app = context.applicationContext
    private val dir: File get() = File(app.filesDir, DIR_NAME)

    private fun file(name: String): File = File(dir, name)

    /** يحفظ النص الكامل ويرجع اسم الملف، أو null لو فشل الحفظ. يُستدعى من خيط خلفي. */
    fun save(text: String): String? {
        if (!app.isUserStorageUnlocked()) return null
        return runCatching {
            dir.mkdirs()
            val bytes = text.toByteArray(Charsets.UTF_8)
            val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(20)
            val name = "$hash.txt"
            val target = file(name)
            if (target.exists()) {
                target.setLastModified(System.currentTimeMillis())
            } else {
                val tmp = File(dir, "tmp_" + System.nanoTime())
                try {
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(target)) return null
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
            }
            name
        }.getOrNull()
    }

    /** يقرأ النص الكامل (من خيط خلفي) أو null لو الملف مفقود */
    fun read(name: String): String? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return null
        return runCatching {
            val f = file(name)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        }.getOrNull()
    }

    fun delete(name: String) {
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return
        runCatching { file(name).delete() }
    }

    fun deleteAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    fun retain(names: Set<String>, minAgeMs: Long) {
        val now = System.currentTimeMillis()
        runCatching {
            dir.listFiles()?.forEach { if (it.name !in names && now - it.lastModified() >= minAgeMs) it.delete() }
        }
    }

    companion object {
        const val DIR_NAME = "clip_texts"
    }
}
