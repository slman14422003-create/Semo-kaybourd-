package com.semo.keyboard.data

import android.content.Context
import com.semo.keyboard.domain.logic.AsciiWordList
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.logic.NgramModel
import com.semo.keyboard.domain.logic.ValidWords
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.Script
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** ما تم تنزيله للغة: كلمات القاموس، وعدد الجمل التي بُني منها نموذج التتمة (0 = لا نموذج منزَّل) */
data class DownloadInfo(val words: Int, val sentences: Int)

/**
 * مكتبة اللغات: يحمّل القاموس المدمج (كلمات شائعة عربية وإنكليزية + قائمة 114 ألف كلمة إنكليزية صالحة للتدقيق
 * من assets، تعمل بدون إنترنت) ونموذج الجمل المدمج، ثم يدمج معهما ما نزّله المستخدم لأي لغة.
 *
 * لكل لغة يُنزَّل ملفان بضغطة واحدة من إعدادات التطبيق (خدمة لوحة المفاتيح نفسها لا تتصل بالإنترنت):
 *  - قاموس مرتّب بالشيوع حتى 100 ألف كلمة: FrequencyWords من ترجمات OpenSubtitles (رخصة CC-BY-SA 4.0)،
 *  - جمل حقيقية من Tatoeba (رخصة CC-BY 2.0 FR) يُبنى منها نموذج ثنائيات وثلاثيات لتوقّع الكلمة التالية وتوليد تتمة الجمل.
 * لا يُحمَّل بالذاكرة إلا اللغات المفعّلة بالإعدادات كي لا تتضخّم الذاكرة.
 */
object DictionaryManager {

    private const val BASE_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018"
    private const val TATOEBA_URL = "https://downloads.tatoeba.org/exports/per_language"
    private const val MAX_WORDS = 100_000

    /** أقصى عدد جمل نقرؤه من Tatoeba (القراءة متدفقة فنوقفها عند الحد دون تنزيل الملف كله) */
    private const val MAX_SENTENCES = 90_000

    /** الجمل المدمجة تُكرَّر بالبناء كي لا يقصّها حدّ التكرار الأدنى لجمل المكتبة الكبيرة */
    private const val SEED_REPEAT = 3

    @Volatile private var appContext: Context? = null

    /** نجرّب القائمة الكاملة (نقرأ أول 100 ألف كلمة صالحة فقط) ثم نرجع لقائمة الـ50 ألف لو تعذّرت */
    private fun urls(language: KeyboardLanguage): List<String> {
        val code = language.code
        return listOf("$BASE_URL/$code/${code}_full.txt", "$BASE_URL/$code/${code}_50k.txt")
    }

    private fun coreAsset(language: KeyboardLanguage): String = "dict/${language.code}_core.txt"
    private fun seedAsset(language: KeyboardLanguage): String = "corpus/${language.code}.txt"

    /** الكلمات الشائعة المدمجة بترتيب الشيوع (من assets؛ متاحة حتى قبل فك قفل الجهاز) */
    private fun readCore(context: Context, language: KeyboardLanguage): List<String> =
        runCatching {
            context.assets.open(coreAsset(language)).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        }.getOrNull().orEmpty()

    /** جمل النموذج المدمج (عربي وإنكليزي) من assets */
    private fun readSeedSentences(context: Context, language: KeyboardLanguage): List<String> =
        runCatching {
            context.assets.open(seedAsset(language)).bufferedReader(Charsets.UTF_8).useLines { lines ->
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

    private fun wordsFile(context: Context, language: KeyboardLanguage): File =
        File(File(context.applicationContext.filesDir, "dict"), "${language.code}.txt")

    private fun modelFile(context: Context, language: KeyboardLanguage): File =
        File(File(context.applicationContext.filesDir, "ngram"), "${language.code}.txt")

    fun hasWords(context: Context, language: KeyboardLanguage): Boolean = wordsFile(context, language).exists()
    fun hasModel(context: Context, language: KeyboardLanguage): Boolean = modelFile(context, language).exists()

    /** اللغة منزَّلة (قاموس موجود على الجهاز) أو مدمجة */
    fun isAvailable(context: Context, language: KeyboardLanguage): Boolean =
        language.builtIn || hasWords(context, language)

    fun hasDownloaded(context: Context): Boolean =
        KeyboardLanguage.values().any { hasWords(context, it) }

    /**
     * يحمّل قاموس ونموذج جمل [language] بالذاكرة (ثقيل: يُستدعى بخيط خلفي). المدمج من assets يُحمَّل دائمًا،
     * أما الملفات المنزَّلة (تخزين مشفّر) فلا تُقرأ قبل فك قفل الجهاز.
     */
    fun load(context: Context, language: KeyboardLanguage) {
        val app = context.applicationContext
        appContext = app
        loadValidWords(app)
        val unlocked = app.isUserStorageUnlocked()

        val wf = wordsFile(app, language)
        val downloaded = if (unlocked && wf.exists()) {
            runCatching { wf.readLines(Charsets.UTF_8).filter { it.isNotBlank() } }.getOrNull().orEmpty()
        } else emptyList()
        val words = merged(app, language, downloaded)
        if (words.isNotEmpty()) BigDictionary.setWords(language, words)

        // نموذج الجمل: المنزَّل لو موجود (يتضمّن الجمل المدمجة)، وإلا يُبنى من الجمل المدمجة
        val mf = modelFile(app, language)
        var model: NgramModel.Model? = null
        if (unlocked && mf.exists()) {
            model = runCatching { mf.useLines(Charsets.UTF_8) { NgramModel.parse(it) } }.getOrNull()
        }
        if (model == null || model.isEmpty) {
            val seed = readSeedSentences(app, language)
            if (seed.isNotEmpty()) model = NgramModel.build(seed, minWordFreq = 1, minCount = 1)
        }
        if (model != null && !model.isEmpty) NgramModel.set(language, model) else NgramModel.clear(language)
    }

    /** يحمّل اللغات المفعّلة بالإعدادات (تُستدعى عند تشغيل الخدمة بخيط خلفي) */
    fun loadAll(context: Context) {
        val app = context.applicationContext
        appContext = app
        val enabled = runCatching {
            runBlocking { SettingsRepository(app).settings.first().enabledLanguages }
        }.getOrNull() ?: listOf(KeyboardLanguage.ENGLISH, KeyboardLanguage.ARABIC)
        for (language in enabled) runCatching { load(app, language) }
    }

    /** يضمن تحميل [language] لو لم تكن محمَّلة (عند التبديل إليها بالكرة الأرضية) */
    fun ensureLoaded(language: KeyboardLanguage) {
        val app = appContext ?: return
        if (BigDictionary.isLoaded(language) || NgramModel.isLoaded(language)) return
        runCatching { load(app, language) }
    }

    /** يفرغ اللغة من الذاكرة (عند تعطيلها) ولا يمسح ملفاتها */
    fun unload(language: KeyboardLanguage) {
        BigDictionary.clear(language)
        NgramModel.clear(language)
    }

    /** يحذف ما نُزِّل للغة ويُبقي المدمج شغّالًا (للعربي والإنكليزي)، أو يفرّغها من الذاكرة */
    fun delete(context: Context, language: KeyboardLanguage) {
        val app = context.applicationContext
        runCatching { wordsFile(app, language).delete() }
        runCatching { modelFile(app, language).delete() }
        if (language.builtIn) load(app, language) else unload(language)
    }

    // ------------------------------------------------------------------
    // التنزيل
    // ------------------------------------------------------------------

    /** ينزّل قاموس [language] ونموذج جمله ويحمّلهما. فشل القاموس = فشل كامل، وفشل الجمل وحده لا يمنع القاموس. */
    fun download(
        context: Context,
        language: KeyboardLanguage,
        onProgress: (String) -> Unit
    ): Result<DownloadInfo> = runCatching {
        val app = context.applicationContext
        appContext = app

        onProgress("جاري تنزيل قاموس ${language.nativeName}...")
        val words = fetchWords(language)
        if (words.size < 1000) error("القائمة المنزّلة صغيرة جدًا")
        writeAtomically(wordsFile(app, language), words.joinToString("\n"))
        onProgress("جاري بناء فهرس القاموس...")
        BigDictionary.setWords(language, merged(app, language, words))

        var sentenceCount = 0
        onProgress("جاري تنزيل جمل ${language.nativeName} لنموذج التتمة...")
        val corpus = runCatching { fetchSentences(language) }.getOrNull().orEmpty()
        if (corpus.size >= 2000) {
            onProgress("جاري بناء نموذج الجمل...")
            val seed = readSeedSentences(app, language)
            val all = ArrayList<String>(corpus.size + seed.size * SEED_REPEAT)
            all.addAll(corpus)
            repeat(SEED_REPEAT) { all.addAll(seed) }
            val model = NgramModel.build(all, minWordFreq = 2, minCount = 2)
            if (!model.isEmpty) {
                writeAtomically(modelFile(app, language), NgramModel.serialize(model))
                NgramModel.set(language, model)
                sentenceCount = corpus.size
            }
        }
        DownloadInfo(words.size, sentenceCount)
    }

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("تعذّر حفظ الملف")
    }

    private fun fetchWords(language: KeyboardLanguage): List<String> {
        for (address in urls(language)) {
            val words = runCatching { fetchWordsFrom(address, language) }.getOrNull()
            if (words != null && words.size >= 1000) return words
        }
        error("تعذّر تنزيل القاموس")
    }

    private fun open(address: String): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.requestMethod = "GET"
        return connection
    }

    private fun fetchWordsFrom(address: String, language: KeyboardLanguage): List<String> {
        val connection = open(address)
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

    /**
     * جمل لغة [language] من Tatoeba (ملف مضغوط bz2 يُفكّ أثناء التنزيل، ونتوقف عند [MAX_SENTENCES] جملة
     * دون تنزيل الباقي). كل سطر: رقم، رمز اللغة، الجملة.
     */
    private fun fetchSentences(language: KeyboardLanguage): List<String> {
        val t = language.tatoeba
        val connection = open("$TATOEBA_URL/$t/${t}_sentences.tsv.bz2")
        connection.readTimeout = 60_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP " + connection.responseCode)
            val out = ArrayList<String>()
            BZip2CompressorInputStream(BufferedInputStream(connection.inputStream, 64 * 1024))
                .bufferedReader(Charsets.UTF_8)
                .useLines { lines ->
                    for (line in lines) {
                        if (out.size >= MAX_SENTENCES) break
                        val parts = line.split('\t')
                        if (parts.size < 3) continue
                        val text = parts[2].trim()
                        // جمل قصيرة جدًا أو طويلة جدًا لا تفيد توقّع الكلمة التالية
                        if (text.length < 8 || text.length > 140) continue
                        out.add(text)
                    }
                }
            return out
        } finally {
            connection.disconnect()
        }
    }

    /** يقبل الكلمات الصالحة للكتابة فقط: بلا أرقام ورموز، وطولها معقول، ونظام كتابتها من نظام اللغة */
    private fun clean(raw: String, language: KeyboardLanguage): String? {
        if (raw.isEmpty()) return null
        return when {
            language == KeyboardLanguage.ENGLISH -> {
                val w = raw.lowercase()
                val ok = w.length in 2..18 && w.all { it in 'a'..'z' || it == '\'' } &&
                    !w.startsWith("'") && !w.endsWith("'")
                if (ok) w else null
            }
            language.script == Script.ARABIC -> {
                val w = raw.filter { it != '\u0640' && it !in '\u064B'..'\u0652' }
                if (w.length in 2..18 && w.all { it.isLetter() && it in '\u0621'..'\u06D3' }) w else null
            }
            language.script == Script.CYRILLIC -> {
                val w = raw.lowercase()
                val ok = w.length in 2..20 && w.all { (it in '\u0400'..'\u04FF' && it.isLetter()) || it == '\'' } &&
                    !w.startsWith("'") && !w.endsWith("'")
                if (ok) w else null
            }
            else -> {
                val w = raw.lowercase()
                val ok = w.length in 2..22 && w.all { (it.isLetter() && it.code < 0x250) || it == '\'' } &&
                    !w.startsWith("'") && !w.endsWith("'")
                if (ok) w else null
            }
        }
    }
}
