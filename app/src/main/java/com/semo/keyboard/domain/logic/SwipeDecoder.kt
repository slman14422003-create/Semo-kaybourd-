package com.semo.keyboard.domain.logic

/**
 * فك الكتابة بالسحب (QuickPath): من قائمة الحروف التي مرّ عليها الإصبع يختار أقرب الكلمات.
 * الشرط: أول حرف وآخر حرف بالكلمة = أول وآخر حرف بالمسار، وباقي حروف الكلمة تظهر بالمسار بنفس الترتيب
 * (الحرف المكرر مثل ll يُعدّ مرة واحدة). الأفضلية للكلمة اللي تغطي أكبر قسم من المسار.
 * منطق بحت بلا Android.
 */
object SwipeDecoder {

    private fun norm(c: Char): Char = when (c) {
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        else -> c.lowercaseChar()
    }

    private fun collapse(s: String): String {
        val sb = StringBuilder()
        for (c in s) if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c)
        return sb.toString()
    }

    private data class Candidate(val word: String, val score: Double)

    fun decode(
        path: List<String>,
        dictionary: List<String>,
        learned: List<String>,
        limit: Int = 3
    ): List<String> {
        val p = path.mapNotNull { it.firstOrNull()?.let { c -> norm(c) } }
        if (p.size < 2) return emptyList()

        val seen = HashSet<String>()
        val candidates = ArrayList<Candidate>()

        fun consider(word: String, rank: Int, bonus: Double) {
            val letters = word.filter { it.isLetter() }
            if (letters.length < 2) return
            val w = collapse(letters.map { norm(it) }.joinToString(""))
            if (w.length < 2 || w.first() != p.first() || w.last() != p.last()) return
            var j = 0
            for (c in p) {
                if (j < w.length && c == w[j]) j++
            }
            if (j < w.length) return
            if (!seen.add(word.lowercase())) return
            val coverage = w.length.toDouble() / p.size
            candidates.add(Candidate(word, coverage * 10.0 + bonus - rank * 0.01))
        }

        learned.forEachIndexed { index, w -> consider(w, index, 1.5) }
        dictionary.forEachIndexed { index, w -> consider(w, index, 0.0) }

        return candidates.sortedByDescending { it.score }.take(limit).map { it.word }
    }
}
