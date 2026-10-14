package com.semo.keyboard.ui.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.ui.anim.IosPush
import com.semo.keyboard.ui.anim.LocalAnimScale
import com.semo.keyboard.ui.anim.scaledSpring
import com.semo.keyboard.ui.anim.scaledTween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.logic.TranslateLanguages
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.ui.theme.SemoKeyboardColors

@Composable
internal fun TranslateBar(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val ui = state.translate ?: return
    val scale = LocalAnimScale.current
    val hasResult = ui.result.isNotEmpty() && !ui.error
    val pickerOpen = state.page == KeyboardPage.TRANSLATE
    val chevron by animateFloatAsState(
        targetValue = if (pickerOpen) 180f else 0f,
        animationSpec = scaledSpring(scale, 0.8f, 380f),
        label = "chevron"
    )
    val border by animateColorAsState(
        targetValue = when {
            ui.error -> BarDanger.copy(alpha = 0.75f)
            ui.loading -> colors.keyAccent.copy(alpha = 0.9f)
            hasResult -> colors.keyAccent.copy(alpha = 0.45f)
            else -> Color.Transparent
        },
        animationSpec = scaledTween(scale, 260),
        label = "border"
    )
    val pillHeight = (stripHeight - 8.dp).coerceAtLeast(34.dp)
    val pillShape = RoundedCornerShape(pillHeight / 2)
    val accentBrush = Brush.linearGradient(
        listOf(colors.keyAccent, colors.keyAccent.copy(alpha = 0.72f))
    )
    val enter = rememberBarEnter()
    val glow by animateFloatAsState(
        targetValue = if (ui.loading) 0.8f else if (hasResult) 0.4f else 0f,
        animationSpec = scaledTween(scale, 280),
        label = "translateGlow"
    )
    val pulse: State<Float> = if (ui.loading) {
        rememberInfiniteTransition(label = "langPulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "langPulseValue"
        )
    } else remember { mutableStateOf(1f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .barEntrance(enter)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressBox(modifier = Modifier.height(stripHeight), onClick = {
            viewModel.onSelectionTick()
            viewModel.toggleTranslatePicker()
        }) { pressed ->
            val press by animateFloatAsState(
                targetValue = if (pressed) 0.92f else 1f,
                animationSpec = scaledSpring(scale, 0.8f, 700f),
                label = "langPress"
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .graphicsLayer {
                        scaleX = press * pulse.value
                        scaleY = press * pulse.value
                    }
                    .height(pillHeight)
                    .clip(pillShape)
                    .background(accentBrush)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = TranslateLanguages.shortCode(state.translateTarget),
                    transitionSpec = {
                        (fadeIn(scaledTween(scale, 200)) + slideInVertically(scaledTween(scale, 240, easing = IosPush)) { it / 2 }) togetherWith
                            (fadeOut(scaledTween(scale, 120)) + slideOutVertically(scaledTween(scale, 200, easing = IosPush)) { -it / 2 })
                    },
                    label = "langCode"
                ) { code ->
                    Text(
                        text = code,
                        color = colors.textOnAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.graphicsLayer { rotationZ = chevron }) {
                    ChevronGlyph(colors.textOnAccent, 9.dp)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        BarCapsule(
            colors = colors,
            height = pillHeight,
            borderColor = border,
            glow = glow,
            glowColor = if (ui.error) BarDanger else colors.keyAccent,
            modifier = Modifier.weight(1f),
            onClick = { if (ui.error) viewModel.retryTranslate() else viewModel.applyTranslation() }
        ) {
            if (ui.loading) Shimmer(colors.keyAccent, Modifier.fillMaxSize())
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shown = when {
                    ui.error -> 1
                    hasResult -> 2
                    ui.loading -> 3
                    else -> 0
                }
                AnimatedContent(
                    targetState = shown to (if (shown == 2) ui.result else ""),
                    transitionSpec = {
                        (fadeIn(scaledTween(scale, 220)) + slideInVertically(scaledTween(scale, 260, easing = IosPush)) { it / 3 }) togetherWith
                            (fadeOut(scaledTween(scale, 120)) + slideOutVertically(scaledTween(scale, 200, easing = IosPush)) { -it / 3 })
                    },
                    modifier = Modifier.weight(1f),
                    label = "result"
                ) { value ->
                    when (value.first) {
                        2 -> Text(
                            text = value.second,
                            color = colors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(textDirection = TextDirection.Content)
                        )
                        1 -> Text(
                            text = stringResource(R.string.translate_error),
                            color = BarDanger,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        3 -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            TypingDots(colors.keyAccent)
                        }
                        else -> Text(
                            text = stringResource(R.string.translate_hint),
                            color = colors.textMuted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (ui.detected.isNotEmpty() && hasResult) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = TranslateLanguages.shortCode(ui.detected),
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = hasResult,
            enter = fadeIn(scaledTween(scale, 180)) + scaleIn(scaledSpring(scale, 0.7f, 500f), initialScale = 0.4f),
            exit = fadeOut(scaledTween(scale, 100)) + scaleOut(scaledTween(scale, 120), targetScale = 0.4f)
        ) {
            Row {
                Spacer(Modifier.width(6.dp))
                PressBox(modifier = Modifier.size(pillHeight), onClick = {
                    viewModel.onSelectionTick()
                    viewModel.applyTranslation()
                }) { pressed ->
                    Box(
                        modifier = Modifier
                            .size(pillHeight - 2.dp)
                            .clip(CircleShape)
                            .background(if (pressed) colors.keyAccent.copy(alpha = 0.7f) else colors.keyAccent),
                        contentAlignment = Alignment.Center
                    ) { CheckGlyph(colors.textOnAccent, 15.dp) }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        BarCloseButton(colors, stripHeight, pillHeight) {
            viewModel.onSelectionTick()
            viewModel.closeTranslate()
        }
    }
}

@Composable
internal fun TranslatePanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    height: Dp,
    viewModel: KeyboardViewModel
) {
    val all = remember { TranslateLanguages.sorted() }
    val recents = state.translateRecents.filter { it in all }
    val names = remember { HashMap<String, Pair<String, String>>() }
    fun info(code: String): Pair<String, String> =
        names.getOrPut(code) { TranslateLanguages.displayName(code) to TranslateLanguages.nativeName(code) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.translate_recent),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                )
            }
            items(recents, key = { "r$it" }) { code ->
                LanguageCell(code, info(code), code == state.translateTarget, colors, viewModel)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.translate_all),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                )
            }
        }
        items(all, key = { "a$it" }) { code ->
            LanguageCell(code, info(code), code == state.translateTarget, colors, viewModel)
        }
    }
}

@Composable
private fun LanguageCell(
    code: String,
    names: Pair<String, String>,
    selected: Boolean,
    colors: SemoKeyboardColors,
    viewModel: KeyboardViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (selected) colors.keyAccent else colors.key, RoundedCornerShape(10.dp))
            .clickable {
                viewModel.onSelectionTick()
                viewModel.setTranslateTarget(code)
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = names.first,
                color = if (selected) colors.textOnAccent else colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content)
            )
            if (names.second.isNotEmpty() && !names.second.equals(names.first, true)) {
                Text(
                    text = names.second,
                    color = if (selected) colors.textOnAccent.copy(alpha = 0.8f) else colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content)
                )
            }
        }
        Text(
            text = TranslateLanguages.shortCode(code),
            color = if (selected) colors.textOnAccent else colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
