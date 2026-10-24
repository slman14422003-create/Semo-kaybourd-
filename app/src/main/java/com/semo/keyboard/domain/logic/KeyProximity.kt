package com.semo.keyboard.domain.logic

object KeyProximity {

    private val arabicRows = listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ذدزروةىظء")
    private val latinRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private val confusion: List<String> = listOf(
        "ضظ", "تط", "سص", "ذزظ", "هة", "ىي", "اء", "ثس", "غع", "حخ", "كق", "ئيى", "ؤو", "دذ", "زر", "ثت", "شس", "ءع"
    )
    private val vowels = "aeiou"

    private val neighbors: Map<Char, Set<Char>> = buildMap {
        for (rows in listOf(arabicRows, latinRows)) {
            for ((r, row) in rows.withIndex()) {
                for ((c, ch) in row.withIndex()) {
                    val set = HashSet<Char>()
                    for (dr in -1..1) {
                        val rr = r + dr
                        if (rr !in rows.indices) continue
                        for (dc in -1..1) {
                            val cc = c + dc
                            if (cc !in rows[rr].indices) continue
                            val n = rows[rr][cc]
                            if (n != ch) set.add(n)
                        }
                    }
                    put(ch, set)
                }
            }
        }
    }

    fun lite(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c in '\u064B'..'\u0652' || c == '\u0640') continue
            sb.append(
                when (c) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    else -> c.lowercaseChar()
                }
            )
        }
        return sb.toString()
    }

    fun substitution(a: Char, b: Char): Float {
        if (a == b) return 0f
        for (g in confusion) if (a in g && b in g) return 0.4f
        if (a in vowels && b in vowels) return 0.7f
        if (neighbors[a]?.contains(b) == true) return 0.6f
        return 1f
    }

    fun cost(a: String, b: String, limit: Float = 2.4f): Float {
        val n = a.length
        val m = b.length
        if (n == 0 || m == 0) return limit + 1f
        if (kotlin.math.abs(n - m) > 2) return limit + 1f
        val d = Array(n + 1) { FloatArray(m + 1) }
        for (i in 0..n) d[i][0] = i.toFloat()
        for (j in 0..m) d[0][j] = j.toFloat()
        for (i in 1..n) {
            var rowMin = Float.MAX_VALUE
            for (j in 1..m) {
                val sub = d[i - 1][j - 1] + substitution(a[i - 1], b[j - 1])
                val del = d[i - 1][j] + if (i > 1 && a[i - 1] == a[i - 2]) 0.7f else 1f
                val ins = d[i][j - 1] + if (j > 1 && b[j - 1] == b[j - 2]) 0.7f else 1f
                var v = minOf(sub, del, ins)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, d[i - 2][j - 2] + 0.8f)
                }
                d[i][j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1f
        }
        return d[n][m]
    }
}
