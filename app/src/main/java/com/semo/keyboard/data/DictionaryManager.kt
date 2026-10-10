package com.semo.keyboard.data

import android.content.Context
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.model.KeyboardLanguage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * يُنزّل قواميس كبيرة مرتبة حسب الشيوع (قوائم FrequencyWords المبنية من ترجمات OpenSubtitles، رخصة CC-BY-SA 4.0)
 * ويخزّنها بملفات على الجهاز، ثم يحمّلها بالذاكرة لتُدمج مع الإكمال التلقائي والكتابة بالسحب.
 * التنزيل يحصل فقط عند ضغط المستخدم زر التنزيل بإعدادات التطبيق، ولا يتصل خدمة لوحة المفاتيح بالإنترنت.
 */
object DictionaryManager {

    private const val BASE_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018"
    private const val MAX_WORDS = 50_000

    private fun url(language: KeyboardLanguage): String =
        if (language == KeyboardLanguage.ARABIC) "$BASE_URL/ar/ar_50k.txt" else "$BASE_URL/en/en_50k.txt"

    private fun dictFile(context: Context, language: KeyboardLanguage): File {
        val name = if (language == KeyboardLanguage.ARABIC) "ar.txt" else "en.txt"
        return File(File(context.applicationContext.filesDir, "dict"), name)
    }

    /** يحمّل الملفات الموجودة بالذاكرة (ثقيل: يُستدعى بخيط خلفي). قبل فك قفل الجهاز لا نفعل شيئًا. */
    fun loadAll(context: Context) {
        val app = context.applicationContext
        if (!app.isUserStorageUnlocked()) return
        for (language in KeyboardLanguage.values()) {
            val f = dictFile(app, language)
            if (!f.exists()) continue
            val words = runCatching { f.readLines(Charsets.UTF_8).filter { it.isNotBlank() } }.getOrNull() ?: continue
            if (words.isNotEmpty()) BigDictionary.setWords(language, words)
        }
    }

    fun hasDownloaded(context: Context): Boolean =
        KeyboardLanguage.values().any { dictFile(context, it).exists() }

    fun delete(context: Context) {
        for (language in KeyboardLanguage.values()) {
            runCatching { dictFile(context, language).delete() }
            BigDictionary.clear(language)
        }
    }

    /** ينزّل القاموسين (إنكليزي وعربي) ويحمّلهما. يرجع (عدد الإنكليزي، عدد العربي) أو فشل. */
    fun download(context: Context, onProgress: (String) -> Unit): Result<Pair<Int, Int>> = runCatching {
        val app = context.applicationContext
        var english = 0
        var arabic = 0
        for (language in listOf(KeyboardLanguage.ENGLISH, KeyboardLanguage.ARABIC)) {
            onProgress(if (language == KeyboardLanguage.ARABIC) "جاري تنزيل القاموس العربي..." else "جاري تنزيل القاموس الإنكليزي...")
            val words = fetch(language)
            if (words.size < 1000) error("القائمة المنزّلة صغيرة جدًا")
            val target = dictFile(app, language)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(words.joinToString("\n"), Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) error("تعذّر حفظ الملف")
            onProgress("جاري بناء الفهرس...")
            BigDictionary.setWords(language, words)
            if (language == KeyboardLanguage.ARABIC) arabic = words.size else english = words.size
        }
        english to arabic
    }

    private fun fetch(language: KeyboardLanguage): List<String> {
        val connection = URL(url(language)).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP " + connection.responseCode)
            val seen = LinkedHashSet<String>()
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (seen.size >= MAX_WORDS) break
                    val word = clean(line.substringBefore(' ').trim(), language) ?: continue
                    seen.add(word)
                }
            }
            return seen.toList()
        } finally {
            connection.disconnect()
        }
    }

    /** يقبل الكلمات الصالحة للكتابة فقط: بلا أرقام ورموز، وطولها معقول */
    private fun clean(raw: String, language: KeyboardLanguage): String? {
        if (raw.isEmpty()) return null
        return if (language == KeyboardLanguage.ARABIC) {
            val w = raw.filter { it != '\u0640' && it !in '\u064B'..'\u0652' }
            if (w.length in 2..18 && w.all { it in '\u0621'..'\u064A' }) w else null
        } else {
            val w = raw.lowercase()
            val ok = w.length in 2..18 && w.all { it in 'a'..'z' || it == '\'' } &&
                !w.startsWith("'") && !w.endsWith("'")
            if (ok) w else null
        }
    }
}
