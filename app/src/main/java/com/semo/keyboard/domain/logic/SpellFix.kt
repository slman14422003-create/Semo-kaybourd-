package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.Script
import kotlin.math.ln

object SpellFix {

    class Scored(val word: String, val rank: Int, val cost: Float, val ctx: Float, val score: Double)

    private fun contextProbs(language: KeyboardLanguage, context: String): Map<String, Float> {
        if (context.isEmpty()) return emptyMap()
        val out = HashMap<String, Float>()
        for ((w, p) in NgramModel.scored(language, "", context)) {
            val k = BigDictionary.key(w)
            val old = out[k]
            if (old == null || old < p) out[k] = p
        }
        return out
    }

    private fun edits1(w: String, alphabet: String): Set<String> {
        val out = HashSet<String>()
        val n = w.length
        for (i in 0 until n) out.add(w.removeRange(i, i + 1))
        for (i in 0 until n - 1) out.add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
        for (i in 0 until n) for (c in alphabet) if (c != w[i]) out.add(w.substring(0, i) + c + w.substring(i + 1))
        for (i in 0..n) for (c in alphabet) out.add(w.substring(0, i) + c + w.substring(i))
        return out
    }

    fun rank(
        word: String,
        language: KeyboardLanguage,
        learnedKeys: Set<String>,
        context: String,
        alphabet: String
    ): List<Scored> {
        val lower = word.lowercase()
        val q = KeyProximity.lite(lower)
        val probs = contextProbs(language, context)
        val seen = HashMap<String, Scored>()

        fun consider(shown: String, rank: Int, cost: Float, learned: Boolean) {
            if (cost <= 0f || cost > 2.0f) return
            val key = BigDictionary.key(shown)
            if (key == BigDictionary.key(lower)) return
            val ctx = probs[key] ?: 0f
            var score = cost * 100.0 + (if (rank >= 0) ln(rank + 2.0) * 6.0 else 70.0) - ctx * 60.0
            if (learned) score -= 40.0
            if (language.script == Script.ARABIC && KeyProximity.lite(shown).firstOrNull() != q.firstOrNull()) score += 25.0
            if (language.script != Script.ARABIC && shown.firstOrNull()?.lowercaseChar() != lower.firstOrNull()) score += 25.0
            val old = seen[key]
            if (old == null || old.score > score) seen[key] = Scored(shown, rank, cost, ctx, score)
        }

        for (h in SpellIndex.candidates(lower, language)) {
            val shown = BigDictionary.wordAt(h.rank, language) ?: continue
            consider(shown, h.rank, h.cost, BigDictionary.key(shown) in learnedKeys)
        }

        if (lower.length in 3..16) {
            for (c in edits1(lower, alphabet)) {
                if (c.length < 2 || !c.any { it.isLetter() }) continue
                val k = BigDictionary.key(c)
                val r = BigDictionary.rankOf(c, language)
                val learned = k in learnedKeys
                val validEn = language == KeyboardLanguage.ENGLISH && ValidWords.english?.contains(c) == true
                if (r < 0 && !learned && !validEn) continue
                val shown = if (r >= 0) BigDictionary.wordAt(r, language) ?: c else c
                val cost = KeyProximity.cost(q, KeyProximity.lite(c))
                consider(shown, r, cost, learned)
            }
        }
        return seen.values.sortedBy { it.score }
    }

    fun autoFix(
        word: String,
        language: KeyboardLanguage,
        learnedKeys: Set<String>,
        context: String,
        alphabet: String,
        commonRank: Int
    ): String? {
        if (word.any { it.isDigit() }) return null
        val ranked = rank(word, language, learnedKeys, context, alphabet)
        val best = ranked.firstOrNull() ?: return null
        if (best.rank < 0 || best.rank >= commonRank) return null
        val limit = when {
            language.script == Script.ARABIC && word.length >= 6 -> 1.3f
            word.length >= 7 -> 1.3f
            else -> 1.05f
        }
        if (best.cost > limit) return null
        val second = ranked.getOrNull(1)
        if (second != null && second.rank >= 0) {
            val close = second.cost <= best.cost + 0.35f && second.rank < best.rank * 3 + 20
            val ctxDecides = best.ctx > 0.03f && best.ctx > second.ctx * 2f
            if (close && !ctxDecides) return null
        }
        return best.word
    }
}
