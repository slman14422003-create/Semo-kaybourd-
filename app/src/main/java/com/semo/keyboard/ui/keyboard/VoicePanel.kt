package com.semo.keyboard.ui.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.VoiceStatus
import com.semo.keyboard.ui.anim.IosPush
import com.semo.keyboard.ui.anim.LocalAnimScale
import com.semo.keyboard.ui.anim.scaledSpring
import com.semo.keyboard.ui.anim.scaledTween
import com.semo.keyboard.ui.theme.SemoKeyboardColors

@Composable
internal fun VoiceBar(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val ui = state.voice ?: return
    val scale = LocalAnimScale.current
    val level = viewModel.voiceLevel.collectAsState()
    val listening = ui.status == VoiceStatus.LISTENING
    val starting = ui.status == VoiceStatus.STARTING
    val failed = ui.status == VoiceStatus.NETWORK || ui.status == VoiceStatus.FAILED ||
        ui.status == VoiceStatus.UNAVAILABLE || ui.status == VoiceStatus.NO_PERMISSION
    val hasText = ui.text.isNotEmpty() && listening
    val pillHeight = (stripHeight - 8.dp).coerceAtLeast(34.dp)
    val pillShape = RoundedCornerShape(pillHeight / 2)
    val micSize = pillHeight - 2.dp

    val micColor by animateColorAsState(
        targetValue = when {
            failed -> BarDanger
            listening || starting -> colors.keyAccent
            else -> colors.keySpecial
        },
        animationSpec = scaledTween(scale, 240),
        label = "micColor"
    )
    val border by animateColorAsState(
        targetValue = when {
            failed -> BarDanger.copy(alpha = 0.7f)
            listening -> colors.keyAccent.copy(alpha = 0.55f)
            else -> colors.chip.copy(alpha = 0f)
        },
        animationSpec = scaledTween(scale, 260),
        label = "voiceBorder"
    )
    val micScale by animateFloatAsState(
        targetValue = if (listening) 1f + level.value * 0.12f else 1f,
        animationSpec = tween(90),
        label = "micScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressBox(modifier = Modifier.size(width = 52.dp, height = stripHeight), onClick = {
            viewModel.onSelectionTick()
            viewModel.toggleVoice()
        }) { pressed ->
            val press by animateFloatAsState(
                targetValue = if (pressed) 0.9f else 1f,
                animationSpec = scaledSpring(scale, 0.8f, 700f),
                label = "micPress"
            )
            Box(contentAlignment = Alignment.Center) {
                PulseRings(active = listening, color = colors.keyAccent, ringSize = stripHeight + 4.dp)
                Box(
                    modifier = Modifier
                        .size(micSize)
                        .graphicsLayer {
                            scaleX = press * micScale
                            scaleY = press * micScale
                        }
                        .clip(CircleShape)
                        .background(micColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_ios_mic),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (listening || starting || failed) colors.textOnAccent else colors.text
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(pillHeight)
                .clip(pillShape)
                .background(colors.chip.copy(alpha = 0.92f))
                .border(1.5.dp, border, pillShape)
                .clickable(enabled = failed || ui.status == VoiceStatus.PAUSED) {
                    if (ui.status == VoiceStatus.NETWORK || ui.status == VoiceStatus.UNAVAILABLE) viewModel.openVoiceSettings()
                    else viewModel.toggleVoice()
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveWave(
                    level = level,
                    active = listening,
                    color = if (failed) BarDanger else colors.keyAccent,
                    modifier = Modifier
                        .width(if (hasText) 46.dp else 84.dp)
                        .height(pillHeight - 12.dp)
                )
                Spacer(Modifier.width(10.dp))
                val label = when {
                    hasText -> ui.text
                    starting -> stringResource(R.string.voice_starting)
                    listening -> stringResource(R.string.voice_listening)
                    ui.status == VoiceStatus.PAUSED -> stringResource(R.string.voice_paused)
                    ui.status == VoiceStatus.NO_PERMISSION -> stringResource(R.string.voice_permission)
                    ui.status == VoiceStatus.UNAVAILABLE -> stringResource(R.string.voice_unavailable)
                    ui.status == VoiceStatus.NETWORK -> stringResource(R.string.voice_network)
                    else -> stringResource(R.string.voice_failed)
                }
                if (hasText) {
                    val scroll = rememberScrollState()
                    LaunchedEffect(ui.text) { scroll.animateScrollTo(scroll.maxValue) }
                    Text(
                        text = label,
                        color = colors.text,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(textDirection = TextDirection.Content),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = fadeEdges(), blendMode = BlendMode.DstIn)
                            }
                            .horizontalScroll(scroll, enabled = false)
                    )
                } else {
                    AnimatedContent(
                        targetState = label,
                        transitionSpec = {
                            (fadeIn(scaledTween(scale, 200)) + slideInVertically(scaledTween(scale, 240, easing = IosPush)) { it / 3 }) togetherWith
                                (fadeOut(scaledTween(scale, 110)) + slideOutVertically(scaledTween(scale, 180, easing = IosPush)) { -it / 3 })
                        },
                        modifier = Modifier.weight(1f),
                        label = "voiceLabel"
                    ) { text ->
                        Text(
                            text = text,
                            color = if (failed) BarDanger else colors.textMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        PressBox(modifier = Modifier.size(width = 44.dp, height = stripHeight), onClick = {
            viewModel.onSelectionTick()
            viewModel.closeVoice()
        }) { pressed ->
            Box(
                modifier = Modifier
                    .size(pillHeight - 8.dp)
                    .clip(CircleShape)
                    .background(if (pressed) colors.keyPressed else colors.keySpecial),
                contentAlignment = Alignment.Center
            ) { CloseGlyph(colors.text, 11.dp) }
        }
    }
}
