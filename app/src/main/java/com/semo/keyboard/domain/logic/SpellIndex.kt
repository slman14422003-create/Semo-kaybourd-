package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage
import java.util.concurrent.ConcurrentHashMap

object SpellIndex {

    class Hit(val rank: Int, val cost: Float)

    private class Idx(val table: LongArray)

    private const val MAX_WORDS = 90_000
    private const val MIN_LEN = 2
    private const val MAX_LEN = 18

    private val indices = ConcurrentHashMap<KeyboardLanguage, Idx>()
    private val locks = ConcurrentHashMap<KeyboardLanguage, Any>()

    fun invalidate(language: KeyboardLanguage) {
        indices.remove(language)
    }

    fun warm(language: KeyboardLanguage) {
        indexOf(language)
    }

    private fun hash(s: CharSequence, skip: Int = -1): Int {
        var h = 1125899907
        for (i in s.indices) {
            if (i == skip) continue
            h = h * 31 + s[i].code
            h = h xor (h ushr 15)
        }
        return h
    }

    private fun indexOf(language: KeyboardLanguage): Idx? {
        indices[language]?.let { return it }
        val lock = locks.getOrPut(language) { Any() }
        synchronized(lock) {
            indices[language]?.let { return it }
            val n = minOf(BigDictionary.size(language), MAX_WORDS)
            if (n == 0) return null
            var table = LongArray(n * 8)
            var size = 0
            fun add(h: Int, rank: Int) {
                if (size == table.size) table = table.copyOf(table.size * 2)
                table[size++] = (h.toLong() shl 32) or rank.toLong()
            }
            for (rank in 0 until n) {
                val w = BigDictionary.wordAt(rank, language) ?: continue
                val k = KeyProximity.lite(w)
                if (k.length !in MIN_LEN..MAX_LEN) continue
                add(hash(k), rank)
                if (k.length >= 3) for (i in k.indices) add(hash(k, i), rank)
            }
            java.util.Arrays.sort(table, 0, size)
            val idx = Idx(table.copyOf(size))
            indices[language] = idx
            return idx
        }
    }

    private fun collect(table: LongArray, h: Int, out: MutableSet<Int>) {
        val lowKey = h.toLong() shl 32
        var lo = 0
        var hi = table.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (table[mid] < lowKey) lo = mid + 1 else hi = mid
        }
        var i = lo
        while (i < table.size && (table[i] shr 32).toInt() == h) {
            out.add((table[i] and 0xFFFFFFFFL).toInt())
            i++
        }
    }

    fun candidates(word: String, language: KeyboardLanguage, maxCost: Float = 2.0f): List<Hit> {
        val idx = indexOf(language) ?: return emptyList()
        val q = KeyProximity.lite(word)
        if (q.length < 3 || q.length > MAX_LEN) return emptyList()
        val ranks = HashSet<Int>()
        collect(idx.table, hash(q), ranks)
        for (i in q.indices) collect(idx.table, hash(q, i), ranks)
        val out = ArrayList<Hit>(ranks.size)
        for (r in ranks) {
            val w = BigDictionary.wordAt(r, language) ?: continue
            val c = KeyProximity.cost(q, KeyProximity.lite(w), maxCost)
            if (c <= maxCost && c > 0f) out.add(Hit(r, c))
        }
        return out
    }
}
