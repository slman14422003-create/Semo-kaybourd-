package com.semo.keyboard.domain.logic

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

object CalcEngine {

    sealed class Outcome {
        data class Ok(val value: BigDecimal) : Outcome()
        data class Fail(val math: Boolean) : Outcome()
    }

    private val MC = MathContext(40, RoundingMode.HALF_EVEN)
    private val OUT = MathContext(15, RoundingMode.HALF_EVEN)
    private val PI = BigDecimal("3.14159265358979323846264338327950288419716939937510")
    private val E = BigDecimal("2.71828182845904523536028747135266249775724709369995")
    private val BD360 = BigDecimal(360)
    private val BD180 = BigDecimal(180)
    private val BD90 = BigDecimal(90)
    private val BD100 = BigDecimal(100)
    private val TWO = BigDecimal(2)

    private val FUNCS = listOf("sin⁻¹", "cos⁻¹", "tan⁻¹", "sin", "cos", "tan", "log", "ln")
    private val TOKENS = listOf(
        "sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sin(", "cos(", "tan(", "log(", "ln(", "10^(", "e^(", "∛(", "√(", "×10^", "Ans", "ˣ√"
    )

    private class CalcError(val math: Boolean) : RuntimeException(null, null, false, false)

    fun evaluate(expr: String, degrees: Boolean, ans: BigDecimal): Outcome {
        val clean = normalize(expr)
        if (clean.isBlank()) return Outcome.Fail(false)
        return try {
            Outcome.Ok(Parser(clean, degrees, ans).parseAll())
        } catch (e: CalcError) {
            Outcome.Fail(e.math)
        } catch (e: NumberFormatException) {
            Outcome.Fail(false)
        } catch (e: ArithmeticException) {
            Outcome.Fail(true)
        } catch (e: StackOverflowError) {
            Outcome.Fail(false)
        }
    }

    private fun normalize(s: String): String = buildString {
        for (c in s) {
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠'))
                '٫' -> append('.')
                '−', '–', '—' -> append('-')
                '·', '⋅', '×' -> append('*')
                '÷' -> append('/')
                ' ' -> {}
                else -> append(c)
            }
        }
    }

    fun format(v: BigDecimal): String {
        var r = v.round(OUT)
        if (r.signum() == 0) return "0"
        r = r.stripTrailingZeros()
        val exp = r.precision() - r.scale() - 1
        if (exp in -9..14) return r.toPlainString()
        val digits = r.unscaledValue().abs().toString()
        val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        return (if (r.signum() < 0) "-" else "") + mantissa + "E" + exp
    }

    fun toArabicDigits(s: String): String = buildString {
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

    /** حذف آخر عنصر: الدالة كاملة (sin( ...) أو الحرف الأخير */
    fun backspace(expr: String): String {
        if (expr.isEmpty()) return expr
        for (t in TOKENS) if (expr.endsWith(t)) return expr.dropLast(t.length)
        return expr.dropLast(1)
    }

    private fun fromDouble(d: Double): BigDecimal {
        if (d.isNaN() || d.isInfinite()) throw CalcError(true)
        return BigDecimal(d).round(OUT)
    }

    private fun bigSqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcError(true)
        if (x.signum() == 0) return BigDecimal.ZERO
        val xd = x.toDouble()
        var g = if (xd.isInfinite() || xd == 0.0) {
            BigDecimal.TEN.pow(maxOf(1, (x.precision() - x.scale()) / 2))
        } else BigDecimal(Math.sqrt(xd))
        repeat(8) { g = g.add(x.divide(g, MC), MC).divide(TWO, MC) }
        return g
    }

    private fun power(base: BigDecimal, exp: BigDecimal): BigDecimal {
        if (base.signum() == 0 && exp.signum() == 0) throw CalcError(true)
        val isInt = exp.signum() == 0 || exp.stripTrailingZeros().scale() <= 0
        if (isInt && exp.abs().compareTo(BigDecimal(100000)) <= 0) {
            val n = exp.toBigInteger().toInt()
            return if (n >= 0) base.pow(n, MC) else {
                if (base.signum() == 0) throw CalcError(true)
                BigDecimal.ONE.divide(base.pow(-n, MC), MC)
            }
        }
        if (base.signum() < 0) throw CalcError(true)
        if (base.signum() == 0) {
            if (exp.signum() < 0) throw CalcError(true)
            return BigDecimal.ZERO
        }
        return fromDouble(Math.pow(base.toDouble(), exp.toDouble()))
    }

    private fun root(index: BigDecimal, radicand: BigDecimal): BigDecimal {
        if (index.signum() == 0) throw CalcError(true)
        if (index.compareTo(TWO) == 0) return bigSqrt(radicand)
        val oddInt = index.stripTrailingZeros().scale() <= 0 && index.toBigInteger().testBit(0)
        if (radicand.signum() < 0) {
            if (!oddInt) throw CalcError(true)
            return power(radicand.negate(), BigDecimal.ONE.divide(index, MC)).negate()
        }
        return power(radicand, BigDecimal.ONE.divide(index, MC))
    }

    private fun factorial(v: BigDecimal): BigDecimal {
        if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0 || v.compareTo(BigDecimal(1000)) > 0) throw CalcError(true)
        var r = BigInteger.ONE
        for (k in 2..v.toInt()) r = r.multiply(BigInteger.valueOf(k.toLong()))
        return BigDecimal(r)
    }

    private class Parser(val s: String, val deg: Boolean, val ans: BigDecimal) {
        var i = 0

        fun parseAll(): BigDecimal {
            val v = expr()
            if (i != s.length) throw CalcError(false)
            return v
        }

        fun expr(): BigDecimal {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v.add(r, MC) else v.subtract(r, MC)
            }
            return v
        }

        fun term(): BigDecimal {
            var v = unary()
            while (i < s.length) {
                val c = s[i]
                if (c == '*') {
                    i++
                    v = v.multiply(unary(), MC)
                } else if (c == '/') {
                    i++
                    val d = unary()
                    if (d.signum() == 0) throw CalcError(true)
                    v = v.divide(d, MC)
                } else if (startsPrimary()) {
                    v = v.multiply(unary(), MC)
                } else break
            }
            return v
        }

        fun unary(): BigDecimal {
            if (i < s.length && s[i] == '-') { i++; return unary().negate() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return power()
        }

        fun power(): BigDecimal {
            val base = postfix()
            if (s.startsWith("ˣ√", i)) {
                i += 2
                return root(base, postfix())
            }
            if (i < s.length && s[i] == '^') {
                i++
                return power(base, signedPower())
            }
            return base
        }

        private fun power(base: BigDecimal, exp: BigDecimal) = CalcEngine.power(base, exp)

        fun signedPower(): BigDecimal {
            if (i < s.length && s[i] == '-') { i++; return signedPower().negate() }
            if (i < s.length && s[i] == '+') { i++; return signedPower() }
            return power()
        }

        fun postfix(): BigDecimal {
            var v = primary()
            while (i < s.length) {
                if (s[i] == '!') { i++; v = factorial(v) }
                else if (s[i] == '%') { i++; v = v.divide(BD100, MC) }
                else break
            }
            return v
        }

        private fun funcAt(): String? = FUNCS.firstOrNull { s.startsWith("$it(", i) }

        fun startsPrimary(): Boolean {
            val c = s[i]
            return c in '0'..'9' || c == '.' || c == '(' || c == 'π' || c == 'e' ||
                c == '√' || c == '∛' || s.startsWith("Ans", i) || funcAt() != null
        }

        private fun closeParen() {
            if (i < s.length && s[i] == ')') i++
            else if (i < s.length) throw CalcError(false)
        }

        fun primary(): BigDecimal {
            if (i >= s.length) throw CalcError(false)
            val c = s[i]
            if (c == '(') {
                i++
                val v = expr()
                closeParen()
                return v
            }
            if (c in '0'..'9' || c == '.') {
                val start = i
                var dot = false
                while (i < s.length && (s[i] in '0'..'9' || s[i] == '.')) {
                    if (s[i] == '.') {
                        if (dot) throw CalcError(false)
                        dot = true
                    }
                    i++
                }
                return BigDecimal(s.substring(start, i))
            }
            if (c == 'π') { i++; return PI }
            if (c == 'e') { i++; return E }
            if (s.startsWith("Ans", i)) { i += 3; return ans }
            if (c == '√') { i++; return bigSqrt(primary()) }
            if (c == '∛') { i++; return fromDouble(Math.cbrt(primary().toDouble())) }
            val f = funcAt() ?: throw CalcError(false)
            i += f.length + 1
            val arg = expr()
            closeParen()
            return apply(f, arg)
        }

        private fun apply(f: String, a: BigDecimal): BigDecimal = when (f) {
            "sin" -> trig(0, a)
            "cos" -> trig(1, a)
            "tan" -> trig(2, a)
            "sin⁻¹" -> inverse(0, a)
            "cos⁻¹" -> inverse(1, a)
            "tan⁻¹" -> inverse(2, a)
            "log" -> {
                if (a.signum() <= 0) throw CalcError(true)
                fromDouble(Math.log10(a.toDouble()))
            }
            else -> {
                if (a.signum() <= 0) throw CalcError(true)
                fromDouble(Math.log(a.toDouble()))
            }
        }

        private fun trig(kind: Int, a: BigDecimal): BigDecimal {
            val x: Double
            if (deg) {
                val r = a.remainder(BD360)
                val m = r.abs().remainder(BD180)
                when (kind) {
                    0 -> if (m.signum() == 0) return BigDecimal.ZERO
                    1 -> if (m.compareTo(BD90) == 0) return BigDecimal.ZERO
                    else -> {
                        if (m.compareTo(BD90) == 0) throw CalcError(true)
                        if (m.signum() == 0) return BigDecimal.ZERO
                    }
                }
                x = r.multiply(PI, MC).divide(BD180, MC).toDouble()
            } else x = a.toDouble()
            val d = when (kind) {
                0 -> Math.sin(x)
                1 -> Math.cos(x)
                else -> Math.tan(x)
            }
            if (kind != 2 && abs(d) < 1e-15) return BigDecimal.ZERO
            return fromDouble(d)
        }

        private fun inverse(kind: Int, a: BigDecimal): BigDecimal {
            val d = a.toDouble()
            if (kind < 2 && abs(d) > 1.0) throw CalcError(true)
            var r = when (kind) {
                0 -> Math.asin(d)
                1 -> Math.acos(d)
                else -> Math.atan(d)
            }
            if (deg) r = Math.toDegrees(r)
            return fromDouble(r)
        }
    }
}
