package com.semo.keyboard.data

import android.content.Context
import com.semo.keyboard.domain.logic.AsciiWordList
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.logic.ValidWords
import com.semo.keyboard.domain.model.KeyboardLanguage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * يحمّل القاموس المدمج (كلمات شائعة عربية وإنكليزية + قائمة 114 ألف كلمة إنكليزية صالحة للتدقيق من assets،
 * تعمل بدون إنترنت)، ثم يدمج معه القاموس الكبير لو نزّله المستخدم.
 *
 * القاموس الكبير: يُنزّل قواميس كبيرة مرتبة حسب الشيوع (قوائم FrequencyWords المبنية من ترجمات OpenSubtitles، رخصة CC-BY-SA 4.0)
 * ويخزّنها بملفات على الجهاز، ثم يحمّلها بالذاكرة لتُدمج مع الإكمال التلقائي والكتابة بالسحب.
 * التنزيل يحصل فقط عند ضغط المستخدم زر التنزيل بإعدادات التطبيق، ولا يتصل خدمة لوحة المفاتيح بالإنترنت.
 */
object DictionaryManager {

    private const val BASE_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018"
    private const val MAX_WORDS = 100_000

    /** نجرّب القائمة الكاملة (نقرأ أول 100 ألف كلمة صالحة فقط) ثم نرجع لقائمة الـ50 ألف لو تعذّرت */
    private fun urls(language: KeyboardLanguage): List<String> {
        val code = if (language == KeyboardLanguage.ARABIC) "ar" else "en"
        return listOf("$BASE_URL/$code/${code}_full.txt", "$BASE_URL/$code/${code}_50k.txt")
    }

    private fun coreAsset(language: KeyboardLanguage): String =
        if (language == KeyboardLanguage.ARABIC) "dict/ar_core.txt" else "dict/en_core.txt"

    /** الكلمات الشائعة المدمجة بترتيب الشيوع (من assets؛ متاحة حتى قبل فك قفل الجهاز) */
    private fun readCore(context: Context, language: KeyboardLanguage): List<String> =
        runCatching {
            context.assets.open(coreAsset(language)).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        }.getOrNull().orEmpty()

    /** المدمج أولًا (له الأولوية بالترتيب) ثم المنزّل بلا تكرار */
    private fun merged(context: Context, language: KeyboardLanguage, downloaded: List<String>): List<String> {
        val all = LinkedHashSet<String>()
        all.addAll(readCore(context, language))
        all.addAll(downloaded)
        return all.toList()
    }

    private fun loadValidWords(context: Context) {
        if (ValidWords.english != null) return
        runCatching {
            val bytes = context.assets.open("dict/en_valid.txt").use { it.readBytes() }
            ValidWords.english = AsciiWordList(bytes)
        }
    }

    private fun dictFile(context: Context, language: KeyboardLanguage): File {
        val name = if (language == KeyboardLanguage.ARABIC) "ar.txt" else "en.txt"
        return File(File(context.applicationContext.filesDir, "dict"), name)
    }

    /**
     * يحمّل القاموس المدمج والمنزَّل بالذاكرة (ثقيل: يُستدعى بخيط خلفي). المدمج من assets يُحمَّل دائمًا،
     * أما الملفات المنزَّلة (تخزين مشفّر) فلا تُقرأ قبل فك قفل الجهاز.
     */
    fun loadAll(context: Context) {
        val app = context.applicationContext
        loadValidWords(app)
        val unlocked = app.isUserStorageUnlocked()
        for (language in KeyboardLanguage.values()) {
            val f = dictFile(app, language)
            val downloaded = if (unlocked && f.exists()) {
                runCatching { f.readLines(Charsets.UTF_8).filter { it.isNotBlank() } }.getOrNull().orEmpty()
            } else emptyList()
            val words = merged(app, language, downloaded)
            if (words.isNotEmpty()) BigDictionary.setWords(language, words)
        }
    }

    fun hasDownloaded(context: Context): Boolean =
        KeyboardLanguage.values().any { dictFile(context, it).exists() }

    /** يحذف الملفات المنزَّلة ويُبقي القاموس المدمج شغّالًا */
    fun delete(context: Context) {
        val app = context.applicationContext
        for (language in KeyboardLanguage.values()) {
            runCatching { dictFile(app, language).delete() }
            val core = readCore(app, language)
            if (core.isEmpty()) BigDictionary.clear(language) else BigDictionary.setWords(language, core)
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
            BigDictionary.setWords(language, merged(app, language, words))
            if (language == KeyboardLanguage.ARABIC) arabic = words.size else english = words.size
        }
        english to arabic
    }

    private fun fetch(language: KeyboardLanguage): List<String> {
        for (address in urls(language)) {
            val words = runCatching { fetchFrom(address, language) }.getOrNull()
            if (words != null && words.size >= 1000) return words
        }
        error("تعذّر تنزيل القاموس")
    }

    private fun fetchFrom(address: String, language: KeyboardLanguage): List<String> {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
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
