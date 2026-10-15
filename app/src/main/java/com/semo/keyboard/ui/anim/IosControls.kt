package com.semo.keyboard.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue

object IosIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = IosIndicationNode(interactionSource)
    override fun hashCode(): Int = -7
    override fun equals(other: Any?): Boolean = other === this
}

private class IosIndicationNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val level = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch { level.snapTo(1f) }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        launch { level.animateTo(0f, tween(320, easing = IosEaseOut)) }
                    else -> Unit
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = level.value
        if (a > 0.001f) drawRect(Color(0xFF8E8E93).copy(alpha = 0.22f * a))
    }
}

@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onColor: Color = Color(0xFF34C759),
    offColor: Color = Color(0xFF39393D)
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "iosSwitch"
    )
    val stretch by animateFloatAsState(
        if (pressed) 6f else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f),
        label = "iosSwitchStretch"
    )
    val track = lerpColor(offColor, onColor, progress)
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .background(track.copy(alpha = if (enabled) 1f else 0.5f), CircleShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled) { onCheckedChange(!checked) }
    ) {
        val travel = 20f - stretch
        Box(
            modifier = Modifier
                .offset(x = (2f + travel * progress).dp, y = 2.dp)
                .size(width = (27f + stretch).dp, height = 27.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .background(Color.White, CircleShape)
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)
