package com.semo.keyboard.ui.keyboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.VoiceStatus
import com.semo.keyboard.ui.theme.SemoKeyboardColors

@Composable
internal fun VoiceBar(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val ui = state.voice ?: return
    val level = viewModel.voiceLevel.collectAsState()
    val listening = ui.status == VoiceStatus.LISTENING
    val starting = ui.status == VoiceStatus.STARTING
    val failed = ui.status == VoiceStatus.NETWORK || ui.status == VoiceStatus.FAILED ||
        ui.status == VoiceStatus.UNAVAILABLE || ui.status == VoiceStatus.NO_PERMISSION
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressBox(modifier = Modifier.size(width = 52.dp, height = 40.dp), onClick = {
            viewModel.onSelectionTick()
            viewModel.toggleVoice()
        }) { pressed ->
            Box(contentAlignment = Alignment.Center) {
                if (listening) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                val s = 1f + level.value * 0.35f
                                scaleX = s
                                scaleY = s
                                alpha = 0.18f + level.value * 0.35f
                            }
                            .background(colors.keyAccent, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                listening || starting -> colors.keyAccent
                                pressed -> colors.keyPressed
                                else -> colors.keySpecial
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_ios_mic),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (listening || starting) colors.textOnAccent else colors.text),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .background(colors.chip, RoundedCornerShape(17.dp))
                .clickable(enabled = failed || ui.status == VoiceStatus.PAUSED) { viewModel.toggleVoice() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasText = ui.text.isNotEmpty() && listening
                val label = when {
                    hasText -> ui.text
                    ui.status == VoiceStatus.STARTING -> stringResource(R.string.voice_starting)
                    ui.status == VoiceStatus.LISTENING -> stringResource(R.string.voice_listening)
                    ui.status == VoiceStatus.PAUSED -> stringResource(R.string.voice_paused)
                    ui.status == VoiceStatus.NO_PERMISSION -> stringResource(R.string.voice_permission)
                    ui.status == VoiceStatus.UNAVAILABLE -> stringResource(R.string.voice_unavailable)
                    ui.status == VoiceStatus.NETWORK -> stringResource(R.string.voice_network)
                    else -> stringResource(R.string.voice_failed)
                }
                Text(
                    text = label,
                    color = if (hasText) colors.text else colors.textMuted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = colors.keyAccent
                    )
                }
            }
        }
        PressBox(modifier = Modifier.size(width = 48.dp, height = 40.dp), onClick = {
            viewModel.onSelectionTick()
            viewModel.closeVoice()
        }) { pressed ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (pressed) colors.keyPressed else colors.keySpecial, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", color = colors.text, fontSize = 13.sp)
            }
        }
    }
}
