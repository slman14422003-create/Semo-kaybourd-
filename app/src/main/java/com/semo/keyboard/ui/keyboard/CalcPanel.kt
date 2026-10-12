package com.semo.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.logic.CalcEngine
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import java.math.BigDecimal

// ============================ حالة الحاسبة ============================

/**
 * جلسة الحاسبة العلمية: التعبير يُخزَّن بحروف ثابتة (أرقام لاتينية، × ÷ −) وتتغيّر أرقام العرض فقط حسب الإعدادات.
 * الحساب بدقة BigDecimal (40 خانة داخليًا) ويُعرض الناتج بـ 15 خانة معنوية.
 */
class CalcSession {
    var expr by mutableStateOf("")
    var degrees by mutableStateOf(true)
    var second by mutableStateOf(false)
    /** ناتج آخر ضغطة على = (null قبلها أو بعد تعديل التعبير) */
    var shown by mutableStateOf<String?>(null)
    var error by mutableStateOf<Boolean?>(null)
    var evaluated by mutableStateOf(false)
    var errorTick by mutableStateOf(0)
    private var ans: BigDecimal = BigDecimal.ZERO

    /** الناتج الحي أثناء الكتابة (null لو التعبير غير مكتمل) */
    fun live(): String? {
        if (evaluated) return shown
        if (expr.isBlank()) return null
        val r = CalcEngine.evaluate(expr, degrees, ans)
        return if (r is CalcEngine.Outcome.Ok) CalcEngine.format(r.value) else null
    }

    private val continuations = setOf("+", "−", "×", "÷", "^2", "^3", "^", "!", "%", "ˣ√", "×10^")

    fun input(token: String) {
        if (evaluated) {
            expr = if (token in continuations) "Ans" else ""
            evaluated = false
            shown = null
            error = null
        }
        error = null
        if (expr.isEmpty() && token in continuations && token != "−") return
        if (expr.length >= 160) return
        if (token == ".") {
            var k = expr.length
            while (k > 0 && (expr[k - 1] in '0'..'9' || expr[k - 1] == '.')) k--
            if (expr.substring(k).contains('.')) return
            if (k == expr.length) { expr += "0."; return }
        }
        if ((token == "+" || token == "×" || token == "÷") && expr.isNotEmpty() && expr.last() in "+−×÷") {
            expr = expr.dropLast(1) + token
            return
        }
        expr += token
    }

    fun delete() {
        if (evaluated) {
            evaluated = false
            shown = null
            error = null
            return
        }
        error = null
        expr = CalcEngine.backspace(expr)
    }

    fun clear() {
        expr = ""
        shown = null
        error = null
        evaluated = false
    }

    fun evaluate() {
        if (expr.isBlank()) return
        if (evaluated) return
        when (val r = CalcEngine.evaluate(expr, degrees, ans)) {
            is CalcEngine.Outcome.Ok -> {
                ans = r.value
                shown = CalcEngine.format(r.value)
                error = null
                evaluated = true
            }
            is CalcEngine.Outcome.Fail -> {
                shown = null
                error = r.math
                errorTick++
            }
        }
    }
}

// ============================ الواجهة ============================

private enum class CKind { FUNC, DIGIT, OP, DELETE, CLEAR, EQUALS, SECOND }

private class CK(
    val label: String,
    val text: String = label,
    val kind: CKind = CKind.FUNC,
    val label2: String? = null,
    val text2: String? = null
)

private fun fn(label: String, text: String, label2: String? = null, text2: String? = null) =
    CK(label, text, CKind.FUNC, label2, text2)

private fun dg(d: String) = CK(d, d, CKind.DIGIT)
private fun op(label: String) = CK(label, label, CKind.OP)

private val CalcRows: List<List<CK>> = listOf(
    listOf(CK("2nd", "", CKind.SECOND), fn("sin", "sin(", "sin⁻¹", "sin⁻¹("), fn("cos", "cos(", "cos⁻¹", "cos⁻¹("),
        fn("(", "("), fn(")", ")"), CK("⌫", "", CKind.DELETE), CK("AC", "", CKind.CLEAR)),
    listOf(fn("tan", "tan(", "tan⁻¹", "tan⁻¹("), fn("log", "log(", "10ˣ", "10^("), fn("ln", "ln(", "eˣ", "e^("),
        dg("7"), dg("8"), dg("9"), op("÷")),
    listOf(fn("√", "√(", "∛", "∛("), fn("x²", "^2", "x³", "^3"), fn("xʸ", "^", "ʸ√x", "ˣ√"),
        dg("4"), dg("5"), dg("6"), op("×")),
    listOf(fn("π", "π"), fn("e", "e"), fn("x!", "!"),
        dg("1"), dg("2"), dg("3"), op("−")),
    listOf(fn("Ans", "Ans"), fn("%", "%"), fn("×10ˣ", "×10^"),
        dg("0"), dg("."), CK("=", "=", CKind.EQUALS), op("+"))
)

private val DeleteInk = Color(0xFFFF453A)

/**
 * لوحة الحاسبة العلمية: مربع الحساب بدل شريط الأدوات (التعبير + الناتج الحي + DEG/RAD)،
 * ثم شبكة 7×5 (دوال علمية | أرقام | عمليات)، وشريط سفلي فيه «أبج» وزر إدراج الناتج بالحقل.
 */
@Composable
internal fun CalcPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    height: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language.rtl
    val calc = viewModel.calc
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        CalcDisplay(calc, state.arabicDigits, arabic, colors, viewModel)
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            CalcRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    row.forEach { key -> CalcKeyView(key, calc, state.arabicDigits, colors, viewModel) }
                }
            }
        }
        PanelBottomBar(arabic, colors, onBack = {
            viewModel.onSelectionTick()
            viewModel.openPage(KeyboardPage.LETTERS)
        }) {
            Spacer(Modifier.weight(1f))
            val result = calc.live()
            val ready = result != null
            PressBox(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .height(36.dp)
                    .width(150.dp),
                onClick = {
                    if (result != null) viewModel.onCalcInsert(displayNumber(result, state.arabicDigits))
                },
                onLongPress = {
                    if (result != null) {
                        val e = displayNumber(calc.expr, state.arabicDigits)
                        viewModel.onCalcInsert("$e = " + displayNumber(result, state.arabicDigits))
                    }
                }
            ) { pressed ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (ready) (if (pressed) colors.keyAccent.copy(alpha = 0.8f) else colors.keyAccent) else colors.keySpecial,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr(arabic, "إدراج الناتج", "Insert result"),
                        color = if (ready) colors.textOnAccent else colors.textMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun displayNumber(s: String, arabicDigits: Boolean): String =
    if (arabicDigits) CalcEngine.toArabicDigits(s) else s

@Composable
private fun CalcDisplay(
    calc: CalcSession,
    arabicDigits: Boolean,
    arabic: Boolean,
    colors: SemoKeyboardColors,
    viewModel: KeyboardViewModel
) {
    val live = calc.live()
    val err = calc.error
    val pulse = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(calc.evaluated) {
        if (calc.evaluated) {
            pulse.snapTo(0.9f)
            pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    LaunchedEffect(calc.errorTick) {
        if (calc.errorTick > 0) {
            for (v in listOf(12f, -10f, 7f, -4f, 0f)) shake.animateTo(v, tween(45))
        }
    }
    val exprShown = displayNumber(calc.expr, arabicDigits).let { if (it.length > 44) "…" + it.takeLast(43) else it }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(
                Brush.verticalGradient(listOf(colors.chip, lerp(colors.chip, colors.panel, 0.4f))),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PressBox(modifier = Modifier.height(22.dp), onClick = {
                viewModel.onSelectionTick()
                calc.degrees = !calc.degrees
            }) { _ ->
                Box(
                    modifier = Modifier
                        .background(colors.keySpecial, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (calc.degrees) "DEG" else "RAD",
                        color = colors.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (calc.second) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(colors.keyAccent, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = "2nd", color = colors.textOnAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                text = if (calc.evaluated && exprShown.isNotEmpty()) "$exprShown =" else exprShown,
                color = colors.textMuted,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.End,
                style = TextStyle(textDirection = TextDirection.Ltr),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }
        val bigText = when {
            err != null -> if (err) tr(arabic, "خطأ رياضي", "Math ERROR") else tr(arabic, "خطأ في الصياغة", "Syntax ERROR")
            live != null -> displayNumber(live, arabicDigits)
            else -> displayNumber("0", arabicDigits)
        }
        Text(
            text = bigText,
            color = when {
                err != null -> DeleteInk
                calc.evaluated -> colors.text
                live != null -> colors.text.copy(alpha = 0.78f)
                else -> colors.textMuted
            },
            fontSize = if (err != null) 20.sp else 28.sp,
            fontWeight = if (calc.evaluated) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            style = TextStyle(textDirection = TextDirection.Ltr),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                    translationX = shake.value * density
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
        )
    }
}

@Composable
private fun RowScope.CalcKeyView(
    key: CK,
    calc: CalcSession,
    arabicDigits: Boolean,
    colors: SemoKeyboardColors,
    viewModel: KeyboardViewModel
) {
    val modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    val shape = RoundedCornerShape(10.dp)

    fun press() {
        when (key.kind) {
            CKind.SECOND -> {
                viewModel.onSelectionTick()
                calc.second = !calc.second
            }
            CKind.CLEAR -> {
                viewModel.onCalcTick(delete = true)
                calc.clear()
            }
            CKind.EQUALS -> {
                viewModel.onCalcTick()
                calc.evaluate()
            }
            CKind.DELETE -> {}
            else -> {
                viewModel.onCalcTick()
                val useSecond = calc.second && key.text2 != null
                calc.input(if (useSecond) key.text2!! else key.text)
                if (calc.second) calc.second = false
            }
        }
    }

    if (key.kind == CKind.DELETE) {
        RepeatKey(modifier = modifier, onTick = {
            viewModel.onCalcTick(delete = true)
            calc.delete()
        }) { pressed ->
            val delBg by animateColorAsState(if (pressed) colors.keyPressed else colors.keySpecial, tween(if (pressed) 0 else 160), label = "calcDel")
            val delScale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.7f, stiffness = 800f), label = "calcDelScale")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = delScale
                        scaleY = delScale
                    }
                    .drawBehind {
                        drawRoundRect(
                            color = colors.keyShadow,
                            topLeft = Offset(0f, 1.dp.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(10.dp.toPx())
                        )
                    }
                    .background(delBg, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ios_backspace),
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        return
    }

    PressBox(modifier = modifier, onClick = { press() }) { pressed ->
        val second = calc.second
        val target = when (key.kind) {
            CKind.DIGIT -> if (pressed) colors.keyPressed else colors.key
            CKind.EQUALS -> if (pressed) colors.keyAccent.copy(alpha = 0.8f) else colors.keyAccent
            CKind.SECOND -> if (second) colors.shiftActive else if (pressed) colors.keyPressed else colors.keySpecial
            CKind.OP -> lerp(colors.keySpecial, colors.keyAccent, if (pressed) 0.42f else 0.22f)
            else -> if (pressed) colors.keyPressed else colors.keySpecial
        }
        val bg by animateColorAsState(target, tween(if (pressed) 0 else 160), label = "calcKey")
        val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.7f, stiffness = 800f), label = "calcScale")
        val ink = when (key.kind) {
            CKind.EQUALS -> colors.textOnAccent
            CKind.SECOND -> if (second) colors.onShiftActive else colors.text
            CKind.CLEAR -> DeleteInk
            CKind.OP -> colors.keyAccent
            else -> colors.text
        }
        val label = when {
            key.kind == CKind.DIGIT -> displayNumber(key.label, arabicDigits)
            key.label == "." -> if (arabicDigits) "٫" else "."
            second && key.label2 != null -> key.label2
            else -> key.label
        }
        val labelSize = when (key.kind) {
            CKind.DIGIT -> 22.sp
            CKind.OP, CKind.EQUALS -> 22.sp
            CKind.CLEAR -> 15.sp
            else -> if (label.length > 3) 12.sp else 15.sp
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    drawRoundRect(
                        color = colors.keyShadow,
                        topLeft = Offset(0f, 1.dp.toPx()),
                        size = size,
                        cornerRadius = CornerRadius(10.dp.toPx())
                    )
                }
                .background(bg, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = ink,
                fontSize = labelSize,
                fontWeight = if (key.kind == CKind.DIGIT) FontWeight.Normal else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}
