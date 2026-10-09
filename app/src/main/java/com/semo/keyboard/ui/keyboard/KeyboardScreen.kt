package com.semo.keyboard.ui.keyboard

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.theme.SemoDimens
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * جذر واجهة اللوحة. نثبّت اتجاه التخطيط LTR دائمًا: مواضع المفاتيح يجب ألا تنعكس
 * على أجهزة اللغة العربية (RTL)، وإلا ينقلب ترتيب الحروف الإنكليزية والعربية معًا.
 */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isDark = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (isDark) SemoKeyboardColors.Dark else SemoKeyboardColors.Light
    val rows = remember(state) { KeyboardLayoutProvider.rows(state) }

    // من أندرويد 15 اللوحة تُرسم خلف شريط التنقل، فنضيف حشوة سفلية بمقداره
    val bottomInsets = if (Build.VERSION.SDK_INT >= 35) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .windowInsetsPadding(bottomInsets)
                .padding(horizontal = SemoDimens.sidePadding)
                .padding(top = 2.dp, bottom = 4.dp)
        ) {
            TopStrip(
                alternates = state.alternates,
                colors = colors,
                onAlternate = viewModel::onAlternateChosen,
                onDismiss = viewModel::dismissAlternates,
                onHide = { viewModel.onKeyPressed(KeyAction.Hide) }
            )

            if (state.page == KeyboardPage.EMOJI) {
                EmojiPanel(colors = colors, onEmoji = { viewModel.onKeyPressed(KeyAction.Character(it)) })
                KeyRow(KeyboardLayoutProvider.emojiBottomRow(), colors, viewModel)
            } else {
                rows.forEach { row -> KeyRow(row, colors, viewModel) }
            }
        }
    }
}

@Composable
private fun TopStrip(
    alternates: List<String>,
    colors: SemoKeyboardColors,
    onAlternate: (String) -> Unit,
    onDismiss: () -> Unit,
    onHide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (alternates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                alternates.forEach { alt ->
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 34.dp)
                            .background(colors.chip, RoundedCornerShape(8.dp))
                            .clickable { onAlternate(alt) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = alt, fontSize = 20.sp, color = colors.text)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "✕",
                color = colors.textMuted,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            Box(Modifier.size(8.dp).background(colors.keyAccent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Semo Keyboard",
                color = colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clickable(onClick = onHide),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_key_hide),
                    contentDescription = "إخفاء",
                    tint = colors.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyRow(row: List<KeyDefinition>, colors: SemoKeyboardColors, viewModel: KeyboardViewModel) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { key ->
            KeyButton(
                def = key,
                colors = colors,
                onKey = viewModel::onKeyPressed,
                onLongPress = viewModel::onKeyLongPressed
            )
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    def: KeyDefinition,
    colors: SemoKeyboardColors,
    onKey: (KeyAction) -> Unit,
    onLongPress: (KeyDefinition) -> Unit
) {
    if (def.isSpacer) {
        Spacer(Modifier.weight(def.weight))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(50), label = "keyScale")
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnLong by rememberUpdatedState(onLongPress)
    val hasAlternates = def.longPressChars.isNotEmpty()
    val isBackspace = def.action == KeyAction.Backspace
    val special = isSpecial(def)

    val bg = when {
        def.isAccent -> colors.keyAccent
        pressed && special -> colors.key
        pressed -> colors.keyPressed
        special -> colors.keySpecial
        else -> colors.key
    }
    val textColor = if (def.isAccent) colors.textOnAccent else colors.text
    val shadowColor = colors.keyShadow

    // منطقة اللمس تشمل الفراغات بين المفاتيح (تقلّل الضغطات الضائعة)، والشكل المرئي أصغر منها
    val longPressHandler: ((Offset) -> Unit)? =
        if (hasAlternates) ({ _: Offset -> currentOnLong(def) }) else null

    Box(
        modifier = Modifier
            .weight(def.weight)
            .height(SemoDimens.keyHeight + SemoDimens.rowSpacing)
            .pointerInput(def) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        if (isBackspace) {
                            // حذف فوري ثم تكرار تلقائي عند الاستمرار بالضغط
                            currentOnKey(KeyAction.Backspace)
                            coroutineScope {
                                val repeat = launch {
                                    delay(400)
                                    while (true) {
                                        currentOnKey(KeyAction.Backspace)
                                        delay(50)
                                    }
                                }
                                tryAwaitRelease()
                                repeat.cancel()
                            }
                        } else {
                            tryAwaitRelease()
                        }
                        pressed = false
                    },
                    onTap = { if (!isBackspace) currentOnKey(def.action) },
                    onLongPress = longPressHandler
                )
            }
            .padding(horizontal = SemoDimens.keySpacing / 2, vertical = SemoDimens.rowSpacing / 2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .drawBehind {
                    drawRoundRect(
                        color = shadowColor,
                        topLeft = Offset(0f, 1.5.dp.toPx()),
                        size = size,
                        cornerRadius = CornerRadius(7.dp.toPx())
                    )
                }
                .background(bg, SemoDimens.keyShape),
            contentAlignment = Alignment.Center
        ) {
            KeyContent(def, textColor)
        }
    }
}

@Composable
private fun KeyContent(def: KeyDefinition, textColor: Color) {
    val iconRes = if (def.textOnly) null else when (def.action) {
        KeyAction.Shift -> if (def.label == "⇪") R.drawable.ic_key_shift_locked else R.drawable.ic_key_shift
        KeyAction.Backspace -> R.drawable.ic_key_backspace
        KeyAction.Globe -> R.drawable.ic_key_globe
        KeyAction.Emoji -> R.drawable.ic_key_emoji
        KeyAction.Enter -> R.drawable.ic_key_enter
        else -> null
    }
    if (iconRes != null) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = def.label,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
    } else {
        val isLetter = def.action is KeyAction.Character
        Text(
            text = def.label,
            color = textColor,
            fontSize = when {
                def.action == KeyAction.Space -> 14.sp
                isLetter -> 22.sp
                def.label.length > 2 -> 14.sp
                else -> 18.sp
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** صفحة الإيموجي: شبكة قابلة للتمرير */
@Composable
private fun EmojiPanel(colors: SemoKeyboardColors, onEmoji: (String) -> Unit) {
    val currentOnEmoji by rememberUpdatedState(onEmoji)
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height((SemoDimens.keyHeight + SemoDimens.rowSpacing) * 3)
    ) {
        items(KeyboardLayoutProvider.emojis) { emoji ->
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .pointerInput(emoji) { detectTapGestures(onTap = { currentOnEmoji(emoji) }) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 24.sp, color = colors.text)
            }
        }
    }
}

private fun isSpecial(def: KeyDefinition): Boolean = when (def.action) {
    KeyAction.Shift, KeyAction.Backspace, KeyAction.Globe, KeyAction.Emoji,
    KeyAction.SwitchToSymbols, KeyAction.SwitchToSymbols2, KeyAction.SwitchToLetters,
    KeyAction.SwitchLanguage -> true
    else -> false
}
