package com.semo.keyboard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    val highlight: Color = Color.White,
    val edgeWidth: Float = 1f,
    val image: ImageBitmap? = null,
    val imageDim: Float = 0f
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

private fun DrawScope.drawCoverImage(img: ImageBitmap) {
    val s = maxOf(size.width / img.width, size.height / img.height)
    if (s <= 0f) return
    val cropW = (size.width / s).toInt().coerceIn(1, img.width)
    val cropH = (size.height / s).toInt().coerceIn(1, img.height)
    drawImage(
        image = img,
        srcOffset = IntOffset((img.width - cropW) / 2, (img.height - cropH) / 2),
        srcSize = IntSize(cropW, cropH),
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium
    )
}

fun Modifier.frostPanel(color: Color, spec: FrostSpec): Modifier = drawBehind {
    val img = spec.image
    if (spec.active && img != null) {
        drawCoverImage(img)
        if (spec.imageDim > 0f) drawRect(Color.Black.copy(alpha = spec.imageDim))
    }
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
        val w = spec.edgeWidth * density
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
        val stroke = spec.edgeWidth * density
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
