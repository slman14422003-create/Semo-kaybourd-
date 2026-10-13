package com.semo.keyboard.domain.logic

import java.util.concurrent.atomic.AtomicInteger

object ModelVersion {
    private val counter = AtomicInteger()
    fun bump() { counter.incrementAndGet() }
    fun current(): Int = counter.get()
}

class LruCache<V>(private val max: Int) {
    private val map = object : LinkedHashMap<String, V>(max, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > max
    }

    fun get(key: String): V? = synchronized(map) { map[key] }
    fun put(key: String, value: V) { synchronized(map) { map[key] = value } }
    fun clear() { synchronized(map) { map.clear() } }
}

object ArabicText {

    private val clitics = listOf("وبال", "وكال", "وفال", "ولل", "وال", "بال", "كال", "فال", "لل", "ال", "و", "ف", "ب", "ل", "ك")
    private val suffixes = listOf("هما", "كما", "ها", "هم", "هن", "كم", "كن", "نا", "ني", "ون", "ين", "ان", "ات", "ه", "ك", "ي", "ة")
    private const val HAMZA_FORMS = "أإآٱ"

    fun isArabicWord(word: String): Boolean {
        val c = word.firstOrNull { it.isLetter() } ?: return false
        return c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F'
    }

    fun clitic(word: String): String {
        for (c in clitics) {
            val rest = word.length - c.length
            if (word.startsWith(c) && rest >= (if (c.length == 1) 3 else 2)) return c
        }
        return ""
    }

    fun stems(word: String): List<String> {
        val out = ArrayList<String>(6)
        val pre = clitic(word)
        val noPre = if (pre.isNotEmpty()) word.substring(pre.length) else word
        if (pre.isNotEmpty()) out.add(noPre)
        for (suf in suffixes) {
            if (noPre.length - suf.length >= 3 && noPre.endsWith(suf)) {
                out.add(noPre.dropLast(suf.length))
                if (pre.isNotEmpty() && word.length - suf.length >= 3) out.add(word.dropLast(suf.length))
            }
        }
        return out.distinct()
    }

    fun canonicalHamza(word: String, language: com.semo.keyboard.domain.model.KeyboardLanguage): String? {
        if (word.length < 4 || word.any { it in HAMZA_FORMS || it in '\u064B'..'\u0652' }) return null
        val rank = BigDictionary.rankOf(word, language)
        if (rank !in 0 until 20_000) return null
        val canonical = BigDictionary.wordAt(rank, language) ?: return null
        if (canonical == word || canonical.length != word.length) return null
        for (i in word.indices) {
            val a = word[i]
            val b = canonical[i]
            if (a == b) continue
            val ok = (a == 'ا' && b in HAMZA_FORMS) || (a == 'و' && b == 'ؤ') || (a == 'ي' && b == 'ئ')
            if (!ok) return null
        }
        return canonical
    }
}
