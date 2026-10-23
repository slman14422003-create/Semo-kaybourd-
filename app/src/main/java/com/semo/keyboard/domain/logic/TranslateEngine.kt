package com.semo.keyboard.domain.logic

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

data class TranslateResult(val text: String, val detected: String)

object TranslateEngine {

    private const val MAX_CHARS = 4500
    private val HOSTS = listOf(
        "https://translate.googleapis.com/translate_a/single",
        "https://clients5.google.com/translate_a/single"
    )

    private val cache = object : LinkedHashMap<String, TranslateResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslateResult>?): Boolean = size > 64
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    fun cached(text: String, target: String): TranslateResult? = synchronized(cache) { cache["$target|$text"] }

    fun translate(text: String, target: String): TranslateResult? {
        val source = text.trim().take(MAX_CHARS)
        if (source.isEmpty()) return TranslateResult("", "")
        cached(source, target)?.let { return it }
        val tl = apiCode(target)
        for (host in HOSTS) {
            val result = runCatching { request(host, source, tl) }.getOrNull()
            if (result != null) {
                synchronized(cache) { cache["$target|$source"] = result }
                return result
            }
        }
        return null
    }

    private fun request(host: String, text: String, tl: String): TranslateResult? {
        val url = URL("$host?client=gtx&sl=auto&tl=$tl&dt=t&dj=1&ie=UTF-8&oe=UTF-8")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            conn.outputStream.use { it.write(("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val sentences = json.optJSONArray("sentences") ?: return null
            val out = StringBuilder()
            for (i in 0 until sentences.length()) {
                out.append(sentences.getJSONObject(i).optString("trans", ""))
            }
            val translated = out.toString()
            if (translated.isEmpty()) return null
            return TranslateResult(translated, json.optString("src", ""))
        } finally {
            conn.disconnect()
        }
    }

    private fun apiCode(code: String): String = when (code) {
        "jv" -> "jw"
        "he" -> "iw"
        else -> code
    }
}

object TranslateLanguages {

    val codes: List<String> = listOf(
        "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca", "ceb", "zh-CN", "zh-TW", "co",
        "hr", "cs", "da", "nl", "en", "eo", "et", "fi", "fr", "fy", "gl", "ka", "de", "el", "gu", "ht", "ha",
        "haw", "iw", "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja", "jv", "kn", "kk", "km", "rw", "ko",
        "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn", "my", "ne",
        "no", "ny", "or", "ps", "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st", "sn", "sd", "si",
        "sk", "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt", "te", "th", "tr", "tk", "uk", "ur",
        "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu"
    )

    private fun locale(code: String): Locale = Locale.forLanguageTag(if (code == "iw") "he" else code)

    fun displayName(code: String): String {
        val name = locale(code).getDisplayName(Locale.getDefault())
        return if (name.isBlank() || name.equals(code, true)) code else name.replaceFirstChar { it.uppercase() }
    }

    fun nativeName(code: String): String {
        val loc = locale(code)
        val name = loc.getDisplayName(loc)
        return if (name.isBlank() || name.equals(code, true)) "" else name.replaceFirstChar { it.uppercase() }
    }

    fun shortCode(code: String): String = code.substringBefore('-').uppercase()

    fun sorted(): List<String> = codes.sortedBy { displayName(it).lowercase() }

    fun defaultTarget(): String = if (Locale.getDefault().language == "en") "ar" else "en"
}
