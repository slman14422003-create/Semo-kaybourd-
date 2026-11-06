package com.semo.keyboard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.semo.keyboard.domain.model.BlurTintMode

@Immutable
data class FrostSpec(
    val active: Boolean = false,
    val keys: Boolean = false,
    val border: Float = 0f,
    val sheen: Float = 0f,
    val highlight: Color = Color.White
)

val LocalFrost = staticCompositionLocalOf { FrostSpec() }

fun SemoKeyboardColors.withBlur(
    keys: Float,
    panelAlpha: Float,
    mode: BlurTintMode,
    dark: Boolean
): SemoKeyboardColors {
    val base = when (mode) {
        BlurTintMode.AUTO -> panel
        BlurTintMode.ACCENT -> lerp(panel.copy(alpha = 1f), keyAccent, if (dark) 0.28f else 0.18f)
        BlurTintMode.LIGHTEN -> lerp(panel.copy(alpha = 1f), Color.White, 0.14f)
        BlurTintMode.DARKEN -> lerp(panel.copy(alpha = 1f), Color.Black, 0.28f)
    }
    val a = panelAlpha.coerceIn(0f, 1f)
    val k = keys.coerceIn(0f, 1f)
    return copy(
        panel = base.copy(alpha = a),
        divider = divider.copy(alpha = divider.alpha * a.coerceAtLeast(0.35f)),
        key = key.copy(alpha = key.alpha * k),
        keyPressed = keyPressed.copy(alpha = keyPressed.alpha * k),
        keySpecial = keySpecial.copy(alpha = keySpecial.alpha * k),
        chip = chip.copy(alpha = chip.alpha * k),
        keyShadow = keyShadow.copy(alpha = keyShadow.alpha * k)
    )
}

fun Modifier.frostPanel(color: Color, spec: FrostSpec): Modifier = drawBehind {
    drawRect(color)
    if (!spec.active) return@drawBehind
    if (spec.sheen > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(spec.highlight.copy(alpha = 0.2f * spec.sheen), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.65f
            )
        )
    }
    if (spec.border > 0f) {
        val w = 1f * density
        drawLine(
            color = spec.highlight.copy(alpha = 0.55f * spec.border),
            start = Offset(0f, w / 2f),
            end = Offset(size.width, w / 2f),
            strokeWidth = w
        )
    }
}

fun DrawScope.drawFrostKey(spec: FrostSpec, radiusPx: Float) {
    if (!spec.active || !spec.keys) return
    val corner = CornerRadius(radiusPx)
    if (spec.sheen > 0f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(spec.highlight.copy(alpha = 0.3f * spec.sheen), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.8f
            ),
            cornerRadius = corner
        )
    }
    if (spec.border > 0f) {
        val stroke = 1f * density
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    spec.highlight.copy(alpha = 0.7f * spec.border),
                    spec.highlight.copy(alpha = 0.1f * spec.border)
                )
            ),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
    }
}
