package com.semo.keyboard.data

import android.content.Context
import com.semo.keyboard.R
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.model.KeyboardLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import java.util.zip.GZIPInputStream

enum class PackFormat { TITLES_GZ, HUNSPELL_DIC, LEIPZIG_TAR }

data class ArabicPack(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val format: PackFormat,
    val urls: List<String>,
    val rankedLimit: Int
)

object ArabicPacks {

    private const val LEIPZIG = "https://downloads.wortschatz-leipzig.de/corpora"
    private const val MIN_LEIPZIG_COUNT = 2L

    val all: List<ArabicPack> = listOf(
        ArabicPack(
            "leipzig_wiki", R.string.pack_leipzig_wiki_title, R.string.pack_leipzig_wiki_desc, PackFormat.LEIPZIG_TAR,
            listOf("$LEIPZIG/ara_wikipedia_2021_1M.tar.gz", "$LEIPZIG/ara_wikipedia_2021_300K.tar.gz"), 60_000
        ),
        ArabicPack(
            "leipzig_news", R.string.pack_leipzig_news_title, R.string.pack_leipzig_news_desc, PackFormat.LEIPZIG_TAR,
            listOf("$LEIPZIG/ara_news_2020_1M.tar.gz", "$LEIPZIG/ara_news_2020_300K.tar.gz"), 60_000
        ),
        ArabicPack(
            "ayaspell", R.string.pack_ayaspell_title, R.string.pack_ayaspell_desc, PackFormat.HUNSPELL_DIC,
            listOf(
                "https://cdn.jsdelivr.net/gh/wooorm/dictionaries@main/dictionaries/ar/index.dic",
                "https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/ar/index.dic"
            ), 0
        ),
        ArabicPack(
            "wiktionary", R.string.pack_wiktionary_title, R.string.pack_wiktionary_desc, PackFormat.TITLES_GZ,
            listOf("https://dumps.wikimedia.org/arwiktionary/latest/arwiktionary-latest-all-titles-in-ns0.gz"), 0
        ),
        ArabicPack(
            "wikipedia", R.string.pack_wikipedia_title, R.string.pack_wikipedia_desc, PackFormat.TITLES_GZ,
            listOf("https://dumps.wikimedia.org/arwiki/latest/arwiki-latest-all-titles-in-ns0.gz"), 0
        )
    )

    val activePack = MutableStateFlow<String?>(null)
    val progressText = MutableStateFlow("")
    val lastResult = MutableStateFlow("")

    private fun dir(context: Context): File = File(File(context.applicationContext.filesDir, "dict"), "packs")
    private fun binFile(context: Context, pack: ArabicPack) = File(dir(context), "ar_${pack.id}.bin")
    private fun rankedFile(context: Context, pack: ArabicPack) = File(dir(context), "ar_${pack.id}.txt")

    fun isInstalled(context: Context, pack: ArabicPack): Boolean = binFile(context, pack).exists()

    fun installedBytes(context: Context, pack: ArabicPack): Long =
        binFile(context, pack).let { if (it.exists()) it.length() else 0L } +
            rankedFile(context, pack).let { if (it.exists()) it.length() else 0L }

    fun totalBytes(context: Context): Long = all.sumOf { installedBytes(context, it) }

    fun wordCount(context: Context, pack: ArabicPack): Int = (binFile(context, pack).length() / 8L).toInt()

    fun rankedWords(context: Context): List<String> {
        val app = context.applicationContext
        if (!app.isUserStorageUnlocked()) return emptyList()
        val out = ArrayList<String>()
        for (pack in all) {
            val file = rankedFile(app, pack)
            if (!file.exists()) continue
            runCatching { file.readLines(Charsets.UTF_8).filterTo(out) { it.isNotBlank() } }
        }
        return out
    }

    fun attach(context: Context) {
        val app = context.applicationContext
        if (!app.isUserStorageUnlocked()) return
        ExtraWords.load(all.map { binFile(app, it) }.filter { it.exists() && it.length() >= 8L })
    }

    fun delete(context: Context, pack: ArabicPack) {
        val app = context.applicationContext
        runCatching { binFile(app, pack).delete() }
        runCatching { rankedFile(app, pack).delete() }
        attach(app)
        DictionaryManager.load(app, KeyboardLanguage.ARABIC)
    }

    fun download(context: Context, pack: ArabicPack, onProgress: (String) -> Unit): Result<Int> {
        synchronized(activePack) {
            if (activePack.value != null) return Result.failure(IllegalStateException("busy"))
            activePack.value = pack.id
        }
        progressText.value = ""
        try {
            return runCatching {
                val app = context.applicationContext
                val report = { message: String ->
                    progressText.value = message
                    onProgress(message)
                }
                val collected = fetch(pack, report)
                if (collected.hashes.size < 500) error("too small")
                dir(app).mkdirs()
                writeBin(binFile(app, pack), collected.hashes)
                val ranked = rankedFile(app, pack)
                if (collected.ranked.isNotEmpty()) {
                    writeText(ranked, collected.ranked.joinToString("\n"))
                } else if (ranked.exists()) {
                    ranked.delete()
                }
                attach(app)
                DictionaryManager.load(app, KeyboardLanguage.ARABIC)
                collected.hashes.size
            }
        } finally {
            activePack.value = null
        }
    }

    private class Collected(val hashes: LongArray, val ranked: List<String>)

    private class Sink(val rankedLimit: Int) {
        var hashes = LongArray(1 shl 16)
        var size = 0
        val ranked = LinkedHashSet<String>()

        fun add(word: String, rankedEligible: Boolean) {
            val key = BigDictionary.key(word)
            if (key.length < 2) return
            if (size == hashes.size) hashes = hashes.copyOf(hashes.size * 2)
            hashes[size++] = ExtraWords.hash(key)
            if (rankedEligible && ranked.size < rankedLimit) ranked.add(word)
        }

        fun finish(): Collected {
            val sorted = hashes.copyOf(size)
            java.util.Arrays.sort(sorted)
            var unique = 0
            for (i in sorted.indices) {
                if (i == 0 || sorted[i] != sorted[i - 1]) sorted[unique++] = sorted[i]
            }
            return Collected(sorted.copyOf(unique), ranked.toList())
        }
    }

    private class CountingStream(
        input: InputStream,
        private val total: Long,
        private val onBytes: (Long, Long) -> Unit
    ) : FilterInputStream(input) {
        private var read = 0L
        private var reported = 0L

        private fun advance(n: Int) {
            if (n <= 0) return
            read += n
            if (read - reported >= 512 * 1024) {
                reported = read
                onBytes(read, total)
            }
        }

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) advance(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            advance(n)
            return n
        }
    }

    private fun fetch(pack: ArabicPack, report: (String) -> Unit): Collected {
        var last: Throwable? = null
        for (address in pack.urls) {
            val attempt = runCatching { fetchFrom(pack, address, report) }
            val value = attempt.getOrNull()
            if (value != null && value.hashes.size >= 500) return value
            last = attempt.exceptionOrNull()
        }
        throw last ?: IllegalStateException("download failed")
    }

    private fun fetchFrom(pack: ArabicPack, address: String, report: (String) -> Unit): Collected {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 90_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP " + connection.responseCode)
            val total = connection.contentLengthLong
            val counting = CountingStream(BufferedInputStream(connection.inputStream, 64 * 1024), total) { done, all ->
                val mb = done / (1024 * 1024)
                report(if (all > 0) "$mb / ${all / (1024 * 1024)} MB" else "$mb MB")
            }
            val sink = Sink(pack.rankedLimit)
            when (pack.format) {
                PackFormat.TITLES_GZ -> readTitles(GZIPInputStream(counting, 64 * 1024), sink)
                PackFormat.HUNSPELL_DIC -> readHunspell(counting, sink)
                PackFormat.LEIPZIG_TAR -> readLeipzig(counting, sink)
            }
            return sink.finish()
        } finally {
            connection.disconnect()
        }
    }

    private fun readTitles(input: InputStream, sink: Sink) {
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                for (part in line.split('_', ' ', '-')) {
                    val word = cleanArabic(part) ?: continue
                    sink.add(word, false)
                }
            }
        }
    }

    private fun readHunspell(input: InputStream, sink: Sink) {
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val stem = line.substringBefore('/').substringBefore('\t').trim()
                val word = cleanArabic(stem) ?: continue
                sink.add(word, false)
            }
        }
    }

    private fun readLeipzig(input: InputStream, sink: Sink) {
        val tar = TarArchiveInputStream(GZIPInputStream(input, 64 * 1024))
        while (true) {
            val entry = tar.nextEntry ?: break
            if (entry.isDirectory || !entry.name.endsWith("-words.txt")) continue
            val reader = tar.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                val parts = line.split('\t')
                if (parts.size < 2) continue
                val count = parts.getOrNull(2)?.trim()?.toLongOrNull()
                if (count != null && count < MIN_LEIPZIG_COUNT) continue
                val word = cleanArabic(parts[1].trim()) ?: continue
                sink.add(word, true)
            }
            return
        }
    }

    private fun cleanArabic(raw: String): String? {
        if (raw.isEmpty()) return null
        val w = raw.filter { it != '\u0640' && it !in '\u064B'..'\u0652' }
        return if (w.length in 2..18 && w.all { it.isLetter() && it in '\u0621'..'\u06D3' }) w else null
    }

    private fun writeBin(target: File, hashes: LongArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream(), 256 * 1024)).use { out ->
            for (h in hashes) out.writeLong(h)
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("write failed")
    }

    private fun writeText(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("write failed")
    }
}

object ExtraWords {

    @Volatile private var sets: List<LongBuffer> = emptyList()

    fun hash(key: String): Long {
        var h = -3750763034362895579L
        for (c in key) {
            h = h xor c.code.toLong()
            h *= 1099511628211L
        }
        return h
    }

    fun load(files: List<File>) {
        val mapped = ArrayList<LongBuffer>(files.size)
        for (file in files) {
            runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    mapped.add(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).asLongBuffer())
                }
            }
        }
        sets = mapped
    }

    fun contains(key: String, language: KeyboardLanguage): Boolean {
        if (language != KeyboardLanguage.ARABIC) return false
        val current = sets
        if (current.isEmpty() || key.length < 2) return false
        val h = hash(key)
        for (buffer in current) {
            var lo = 0
            var hi = buffer.limit() - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val v = buffer.get(mid)
                if (v < h) lo = mid + 1 else if (v > h) hi = mid - 1 else return true
            }
        }
        return false
    }
}
