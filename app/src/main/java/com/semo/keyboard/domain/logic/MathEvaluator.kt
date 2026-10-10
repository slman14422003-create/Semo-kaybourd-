package com.semo.keyboard.domain.logic

import com.semo.keyboard.domain.model.MathResult
import kotlin.math.abs
import kotlin.math.pow

/**
 * حساب العمليات المكتوبة (مثل iOS 18): لما تكتب 60*30+20*4^3 يظهر الناتج بشريط الاقتراحات.
 * منطق بحت بلا Android. يدعم + - * / × ÷ ^ والأقواس والأرقام العربية الهندية.
 */
object MathEvaluator {

    private fun isDigitChar(c: Char) = c in '0'..'9' || c in '٠'..'٩'

    private fun isExprChar(c: Char) = isDigitChar(c) || c in "+-*/×÷^().٫ "

    private val operatorBetweenOperands = Regex("[0-9)][+\\-*/^][-+]?[0-9(.]")

    /** يلتقط آخر عملية حسابية قبل المؤشر. يرجع null لو ما في عملية صالحة. */
    fun detect(before: String): MathResult? {
        if (before.isEmpty()) return null
        var end = before.length
        while (end > 0 && (before[end - 1] == ' ' || before[end - 1] == '=')) end--
        if (end == 0) return null

        var start = end
        while (start > 0 && isExprChar(before[start - 1])) start--

        // أول رقم أو قوس بالتعبير (نتجاوز الفراغات والعلامات الزائدة من جملة قبله مثل "word - 5+3")
        var first = start
        while (first < end && !(isDigitChar(before[first]) || before[first] == '(')) first++
        if (first >= end) return null
        // سالب صريح ملتصق بالرقم (-5+3) يبقى جزءًا من التعبير
        if (first - 1 >= start && before[first - 1] == '-' &&
            (first - 1 == 0 || before[first - 2] == ' ' || before[first - 2] == '(')
        ) first--
        // جزء من كلمة (abc123+4) ما بنحسبه
        if (first > 0 && before[first - 1].isLetter()) return null

        val raw = before.substring(first, end).trim()
        if (raw.isEmpty()) return null

        val hasArabicDigits = raw.any { it in '٠'..'٩' }
        val normalized = raw
            .map { c ->
                when {
                    c in '٠'..'٩' -> '0' + (c - '٠')
                    c == '٫' -> '.'
                    c == '×' -> '*'
                    c == '÷' -> '/'
                    else -> c
                }
            }
            .filter { it != ' ' }
            .joinToString("")

        if (!operatorBetweenOperands.containsMatchIn(normalized)) return null
        // تواريخ وأرقام هواتف (2026-10-09 / 9/10/2026) مو عمليات
        if (!raw.contains(' ')) {
            if (normalized.all { it.isDigit() || it == '-' } && normalized.count { it == '-' } >= 2) return null
            if (normalized.all { it.isDigit() || it == '/' } && normalized.count { it == '/' } >= 2) return null
        }

        val value = Parser(normalized).parse() ?: return null
        val text = format(value) ?: return null
        val shown = if (hasArabicDigits) toArabic(text) else text
        return MathResult(expression = raw, value = shown)
    }

    private fun toArabic(s: String): String = buildString {
        for (c in s) {
            append(
                when {
                    c in '0'..'9' -> '٠' + (c - '0')
                    c == '.' -> '٫'
                    else -> c
                }
            )
        }
    }

    private fun format(v: Double): String? {
        if (v.isNaN() || v.isInfinite() || abs(v) >= 1e15) return null
        if (v == Math.rint(v)) return v.toLong().toString()
        val s = String.format(java.util.Locale.US, "%.10f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0" || s.isEmpty()) "0" else s
    }

    /** محلل تعبيرات بالنزول التكراري: الأسّ أعلى أولوية، ثم * /، ثم + - */
    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double? {
            val v = expr() ?: return null
            return if (i == s.length) v else null
        }

        private fun expr(): Double? {
            var v = term() ?: return null
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term() ?: return null
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double? {
            var v = unary() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = unary() ?: return null
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun unary(): Double? {
            if (i < s.length && (s[i] == '-' || s[i] == '+')) {
                val negative = s[i++] == '-'
                val v = unary() ?: return null
                return if (negative) -v else v
            }
            return power()
        }

        private fun power(): Double? {
            val base = primary() ?: return null
            if (i < s.length && s[i] == '^') {
                i++
                val exponent = unary() ?: return null
                return base.pow(exponent)
            }
            return base
        }

        private fun primary(): Double? {
            if (i >= s.length) return null
            if (s[i] == '(') {
                i++
                val v = expr() ?: return null
                if (i >= s.length || s[i] != ')') return null
                i++
                return v
            }
            val begin = i
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.')) i++
            if (begin == i) return null
            return s.substring(begin, i).toDoubleOrNull()
        }
    }
}
