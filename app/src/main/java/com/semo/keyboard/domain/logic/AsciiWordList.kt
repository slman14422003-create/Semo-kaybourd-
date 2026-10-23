package com.semo.keyboard.domain.logic

/**
 * قائمة كلمات إنكليزية صغيرة الحروف (a-z) مرتبة أبجديًا، محفوظة كبايتات خام + فهرس مواضع بدل
 * كائنات String: نحو 114 ألف كلمة بحوالي 1.5MB من الذاكرة، والبحث بالتنصيف. منطق بحت بلا Android.
 */
class AsciiWordList(private val bytes: ByteArray) {

    private val starts: IntArray
    val size: Int

    init {
        val newline = '\n'.code.toByte()
        var n = 0
        for (b in bytes) if (b == newline) n++
        starts = IntArray(n + 1)
        var idx = 0
        var s = 0
        for (i in bytes.indices) {
            if (bytes[i] == newline) {
                starts[idx++] = s
                s = i + 1
            }
        }
        starts[idx] = s
        size = n
    }

    /** يقارن الكلمة رقم [i] بالنص [w]: سالب لو أصغر، 0 لو متساويتان، موجب لو أكبر */
    private fun compareAt(i: Int, w: String): Int {
        var p = starts[i]
        val end = starts[i + 1] - 1
        var j = 0
        while (p < end && j < w.length) {
            val a = bytes[p].toInt() and 0xFF
            val b = w[j].code
            if (a != b) return a - b
            p++
            j++
        }
        return (end - p) - (w.length - j)
    }

    private fun lowerBound(w: String): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareAt(mid, w) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun contains(word: String): Boolean {
        if (word.isEmpty()) return false
        val i = lowerBound(word)
        return i < size && compareAt(i, word) == 0
    }

    private fun wordAt(i: Int): String =
        String(bytes, starts[i], starts[i + 1] - 1 - starts[i], Charsets.US_ASCII)

    /** أقصر [limit] كلمات تبدأ بـ [prefix] (وأطول منه). يفحص حتى 4000 مرشّح فقط للسرعة */
    fun withPrefix(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty() || limit <= 0 || !prefix.all { it in 'a'..'z' }) return emptyList()
        val best = ArrayList<String>(limit + 1)
        var i = lowerBound(prefix)
        var scanned = 0
        while (i < size && scanned < MAX_SCAN) {
            if (compareAt(i, prefix) < 0) { i++; continue }
            val w = wordAt(i)
            if (!w.startsWith(prefix)) break
            if (w.length > prefix.length) {
                var pos = best.size
                while (pos > 0 && best[pos - 1].length > w.length) pos--
                if (pos < limit) {
                    best.add(pos, w)
                    if (best.size > limit) best.removeAt(best.size - 1)
                }
            }
            i++
            scanned++
        }
        return best
    }

    private companion object {
        const val MAX_SCAN = 4000
    }
}

/** قائمة الكلمات الإنكليزية الصالحة (للتدقيق الإملائي وإكمال الكلمات النادرة)، تُحمَّل من assets */
object ValidWords {
    @Volatile var english: AsciiWordList? = null
}
