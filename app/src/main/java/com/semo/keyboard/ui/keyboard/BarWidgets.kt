package com.semo.keyboard.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.semo.keyboard.ui.anim.LocalAnimScale
import com.semo.keyboard.ui.anim.scaledSpring
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

internal val BarDanger = Color(0xFFFF453A)

@Composable
internal fun LiveWave(
    level: State<Float>,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 28
) {
    val history = remember(bars) { FloatArray(bars) }
    val tick = remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "wave")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "wavePhase"
    )
    LaunchedEffect(active, bars) {
        if (!active) {
            history.fill(0f)
            tick.intValue++
        }
        while (active) {
            delay(46)
            System.arraycopy(history, 1, history, 0, bars - 1)
            val target = level.value.coerceIn(0f, 1f)
            val previous = history[bars - 2]
            history[bars - 1] = previous + (target - previous) * 0.65f
            tick.intValue++
        }
    }
    Canvas(modifier = modifier) {
        tick.intValue
        val p = phase.value
        val gap = size.width / bars
        val barWidth = gap * 0.56f
        val mid = size.height / 2f
        val minH = barWidth
        for (i in 0 until bars) {
            val envelope = sin(PI * (i + 0.5) / bars).toFloat().coerceIn(0f, 1f)
            val breathe = if (active) 0.10f + 0.07f * sin(p + i * 0.55f) else 0.02f
            val amp = (history[i] * 1.15f + breathe).coerceIn(0f, 1f)
            val h = max(minH, size.height * amp * (0.35f + 0.65f * envelope))
            val x = i * gap + (gap - barWidth) / 2f
            val fresh = 0.30f + 0.70f * (i + 1f) / bars
            drawRoundRect(
                color = color.copy(alpha = if (active) fresh else 0.30f),
                topLeft = Offset(x, mid - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
internal fun PulseRings(
    active: Boolean,
    color: Color,
    ringSize: Dp,
    modifier: Modifier = Modifier
) {
    if (!active) {
        Box(modifier.size(ringSize))
        return
    }
    val transition = rememberInfiniteTransition(label = "rings")
    val a = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "ringA"
    )
    val b = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, delayMillis = 850, easing = LinearEasing)),
        label = "ringB"
    )
    Canvas(modifier = modifier.size(ringSize)) {
        val core = size.minDimension * 0.5f
        listOf(a.value, b.value).forEach { t ->
            val r = core * (0.55f + 0.45f * t)
            drawCircle(color = color.copy(alpha = 0.34f * (1f - t)), radius = r)
        }
    }
}

@Composable
internal fun Shimmer(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val t = transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerT"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val center = t.value * w
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.28f), Color.Transparent),
                startX = center - w * 0.3f,
                endX = center + w * 0.3f
            )
        )
    }
}

@Composable
internal fun TypingDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    val t = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dotsT"
    )
    Canvas(modifier = modifier.size(width = 24.dp, height = 12.dp)) {
        val r = size.height * 0.2f
        for (i in 0 until 3) {
            val lift = (sin(t.value - i * 0.9f) * 0.5f + 0.5f)
            drawCircle(
                color = color.copy(alpha = 0.35f + 0.65f * lift),
                radius = r * (0.8f + 0.4f * lift),
                center = Offset(size.width * (0.18f + i * 0.32f), size.height / 2f - lift * r * 0.8f)
            )
        }
    }
}

@Composable
internal fun CloseGlyph(color: Color, glyphSize: Dp = 12.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val s = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), s.width, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), s.width, StrokeCap.Round)
    }
}

@Composable
internal fun CheckGlyph(color: Color, glyphSize: Dp = 14.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.80f)
            lineTo(size.width * 0.90f, size.height * 0.22f)
        }
        drawPath(
            path,
            color,
            style = Stroke(size.minDimension * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
internal fun ChevronGlyph(color: Color, glyphSize: Dp = 10.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val path = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.32f)
            lineTo(size.width * 0.5f, size.height * 0.72f)
            lineTo(size.width * 0.9f, size.height * 0.32f)
        }
        drawPath(
            path,
            color,
            style = Stroke(size.minDimension * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

internal fun fadeEdges(): Brush = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.07f to Color.Black,
    0.93f to Color.Black,
    1f to Color.Transparent
)

@Composable
internal fun rememberBarEnter(): State<Float> {
    val scale = LocalAnimScale.current
    val enter = remember { Animatable(if (scale <= 0.001f) 1f else 0f) }
    LaunchedEffect(scale) { enter.animateTo(1f, scaledSpring(scale, 0.82f, 420f)) }
    return enter.asState()
}

internal fun Modifier.barEntrance(enter: State<Float>): Modifier = graphicsLayer {
    val e = enter.value
    alpha = e.coerceIn(0f, 1f)
    translationY = (1f - e) * 8.dp.toPx()
    val s = 0.96f + 0.04f * e
    scaleX = s
    scaleY = s
}

@Composable
internal fun BarCapsule(
    colors: SemoKeyboardColors,
    height: Dp,
    borderColor: Color,
    glow: Float,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .height(height)
            .drawBehind {
                if (glow > 0.01f) {
                    val grow = 3.dp.toPx() * glow
                    drawRoundRect(
                        color = glowColor.copy(alpha = 0.22f * glow),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2f, size.height + grow * 2f),
                        cornerRadius = CornerRadius(size.height / 2f + grow)
                    )
                }
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.chip.copy(alpha = 0.97f), colors.chip.copy(alpha = 0.84f))
                )
            )
            .border(1.5.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart,
        content = content
    )
}

@Composable
internal fun BarCloseButton(
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    pillHeight: Dp,
    onClick: () -> Unit
) {
    val scale = LocalAnimScale.current
    PressBox(modifier = Modifier.size(width = 44.dp, height = stripHeight), onClick = onClick) { pressed ->
        val press by animateFloatAsState(
            targetValue = if (pressed) 0.84f else 1f,
            animationSpec = scaledSpring(scale, 0.55f, 600f),
            label = "closePress"
        )
        val spin by animateFloatAsState(
            targetValue = if (pressed) 90f else 0f,
            animationSpec = scaledSpring(scale, 0.7f, 380f),
            label = "closeSpin"
        )
        Box(
            modifier = Modifier
                .size(pillHeight - 8.dp)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                    rotationZ = spin
                }
                .clip(CircleShape)
                .background(if (pressed) colors.keyPressed else colors.keySpecial),
            contentAlignment = Alignment.Center
        ) { CloseGlyph(colors.text, 11.dp) }
    }
}
