package com.semo.keyboard.ui.keyboard

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Frosted glass surface, three layers (back to front):
 *  1. fallback backdrop: only when the system cannot blur the app behind us. A tiny downscaled image, softened
 *     further on the GPU with RenderEffect (API 31+). It is a layer of this view, so it can only blur its own pixels.
 *  2. tint: translucent white or black, the "rgba(255,255,255,0.4)" layer. [tint] already carries the alpha.
 *  3. edge highlight: 1dp gradient stroke, bright at the top and faint at the bottom, like light on glass.
 * The real blur of the app underneath is done by the window compositor (see GlassWindowController).
 */
@Composable
fun FrostedGlassKeyboardContainer(
    enabled: Boolean,
    dark: Boolean,
    tint: Color,
    backdrop: ImageBitmap?,
    backdropAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = if (enabled) modifier.clip(RectangleShape) else modifier) {
        if (enabled && backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .gpuBlur(18.dp)
                    .drawBehind {
                        drawImage(
                            image = backdrop,
                            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                            alpha = backdropAlpha,
                            filterQuality = FilterQuality.Medium
                        )
                    }
            )
        }
        Box(modifier = Modifier.matchParentSize().background(tint))
        if (enabled) {
            val top = if (dark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.95f)
            val bottom = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.25f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Brush.verticalGradient(listOf(top, bottom)), RectangleShape)
            )
        }
        content()
    }
}

/**
 * GPU blur of this layer's own content (RenderEffect, API 31+; no-op below). No bitmap is copied on the CPU:
 * the effect runs in RenderThread, so memory cost is one temporary GPU texture the size of the layer.
 */
fun Modifier.gpuBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        graphicsLayer {
            val r = radius.toPx()
            renderEffect = BlurEffect(r, r, TileMode.Clamp)
        }
    } else this
