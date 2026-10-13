package com.semo.keyboard.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.semo.keyboard.ui.appString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.KeyboardTheme
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.anim.IosEaseInOut
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import com.semo.keyboard.ui.theme.SemoPalette
import com.semo.keyboard.ui.theme.semoColors
import kotlinx.coroutines.launch

@StringRes
internal fun themeNameRes(theme: KeyboardTheme): Int = when (theme) {
    KeyboardTheme.IOS -> R.string.theme_ios
    KeyboardTheme.MIDNIGHT -> R.string.theme_midnight
    KeyboardTheme.OCEAN -> R.string.theme_ocean
    KeyboardTheme.FOREST -> R.string.theme_forest
    KeyboardTheme.ROSE -> R.string.theme_rose
    KeyboardTheme.SUNSET -> R.string.theme_sunset
    KeyboardTheme.LAVENDER -> R.string.theme_lavender
    KeyboardTheme.GRAPHITE -> R.string.theme_graphite
    KeyboardTheme.MINT -> R.string.theme_mint
}

@Composable
private fun animatedColor(target: Color): Color =
    animateColorAsState(target, tween(320, easing = IosEaseInOut), label = "themeColor").value

@Composable
internal fun ThemePickerBlock(repo: SettingsRepository, settings: SemoSettings) {
    val scope = rememberCoroutineScope()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            LargePreview(semoColors(settings.style, dark, settings.keyboardTheme))
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KeyboardTheme.values().forEach { theme ->
                ThemeCard(
                    theme = theme,
                    colors = semoColors(settings.style, dark, theme),
                    selected = theme == settings.keyboardTheme,
                    onClick = { scope.launch { repo.setKeyboardTheme(theme) } }
                )
            }
        }
    }
}

@Composable
private fun LargePreview(target: SemoKeyboardColors) {
    val panel = animatedColor(target.panel)
    val key = animatedColor(target.key)
    val special = animatedColor(target.keySpecial)
    val accent = animatedColor(target.keyAccent)
    val muted = animatedColor(target.textMuted)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(panel)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(muted.copy(alpha = 0.35f))
                )
            }
        }
        KeyRow(10, key, accent = null)
        KeyRow(9, key, accent = null)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1.4f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(special))
            Box(Modifier.weight(7f).height(26.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(key)) }
                }
            }
            Box(Modifier.weight(1.4f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(special))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1.4f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(special))
            Box(Modifier.weight(5f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(key))
            Box(Modifier.weight(2.2f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(accent))
        }
    }
}

@Composable
private fun KeyRow(count: Int, key: Color, accent: Color?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count) {
            Box(
                Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (accent != null && it == count - 1) accent else key)
            )
        }
    }
}

@Composable
private fun ThemeCard(theme: KeyboardTheme, colors: SemoKeyboardColors, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (selected) 1.04f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "themeCardScale"
    )
    val ring = animatedColor(if (selected) colors.keyAccent else SemoPalette.Stroke)
    Column(
        modifier = Modifier
            .width(92.dp)
            .scale(scale)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 66.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panel)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, ring), RoundedCornerShape(14.dp))
                .padding(6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(6) { Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(colors.key)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(1.2f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(colors.keySpecial))
                    Box(Modifier.weight(3f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(colors.key))
                    Box(Modifier.weight(1.6f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(colors.keyAccent))
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(16.dp)
                        .background(colors.keyAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = colors.textOnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            text = appString(themeNameRes(theme)),
            color = if (selected) SemoPalette.TextPrimary else SemoPalette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
