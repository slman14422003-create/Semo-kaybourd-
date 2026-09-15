package com.ios26.keyboard.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ios26.keyboard.R
import com.ios26.keyboard.domain.logic.KeyboardLayoutProvider
import com.ios26.keyboard.domain.model.KeyAction
import com.ios26.keyboard.domain.model.KeyDefinition
import com.ios26.keyboard.domain.model.KeyboardUiState
import com.ios26.keyboard.domain.model.ShiftState
import com.ios26.keyboard.ui.theme.IOSKeyboardColors
import com.ios26.keyboard.ui.theme.IOSKeyboardDimens
import com.ios26.keyboard.ui.theme.IOSKeyboardShapes
import kotlinx.coroutines.delay

/**
 * الجذر البصري للوحة: خلفية متكيفة مع الوضع الداكن/الفاتح، ثم صفوف المفاتيح
 * المبنية ديناميكيًا حسب الصفحة الحالية وحالة Shift.
 */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val rows = remember(state.page, state.shiftState) {
        KeyboardLayoutProvider.rows(state.page, state.shiftState)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IOSKeyboardColors.background(state.isDarkTheme))
            .padding(
                start = IOSKeyboardDimens.keyboardPadding,
                end = IOSKeyboardDimens.keyboardPadding,
                top = 8.dp,
                bottom = 6.dp
            ),
        verticalArrangement = Arrangement.spacedBy(IOSKeyboardDimens.rowSpacing)
    ) {
        rows.forEach { row ->
            KeyRow(row = row, state = state, onKeyPressed = viewModel::onKeyPressed)
        }
    }
}

@Composable
private fun KeyRow(
    row: List<KeyDefinition>,
    state: KeyboardUiState,
    onKeyPressed: (KeyAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(IOSKeyboardDimens.keySpacing)
    ) {
        row.forEach { key ->
            KeyButton(
                definition = key,
                isDark = state.isDarkTheme,
                weight = key.weight,
                hapticEnabled = state.hapticEnabled,
                onKeyPressed = onKeyPressed
            )
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    definition: KeyDefinition,
    isDark: Boolean,
    weight: Float,
    hapticEnabled: Boolean,
    onKeyPressed: (KeyAction) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var manualPressed by remember { mutableStateOf(false) }
    val activePressed = isPressed || manualPressed
    val scale by animateFloatAsState(
        targetValue = if (activePressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 60),
        label = "keyScale"
    )
    val haptics = LocalHapticFeedback.current
    var showAlternates by remember { mutableStateOf(false) }

    fun vibrate() {
        // نلف الاستدعاء بـ runCatching: بعض الأجهزة/الروم قد لا تدعم النوع فما نكسر اللوحة كلها لأجل اهتزاز بسيط
        if (hapticEnabled) runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    fun commit(action: KeyAction) {
        vibrate()
        onKeyPressed(action)
    }

    // تكرار الحذف تلقائيًا عند الاستمرار بالضغط على ⌫، متل أي لوحة مفاتيح نظامية حقيقية
    val pressedState = rememberUpdatedState(activePressed)
    LaunchedEffect(activePressed, definition.action) {
        if (definition.action == KeyAction.Backspace && activePressed) {
            delay(380)
            while (pressedState.value) {
                commit(KeyAction.Backspace)
                delay(55)
            }
        }
    }

    val backgroundColor = when {
        definition.isAccent -> IOSKeyboardColors.keyAccent(isDark)
        isSpecialKey(definition) -> IOSKeyboardColors.keySpecial(isDark)
        else -> IOSKeyboardColors.key(isDark)
    }
    val textColor = if (definition.isAccent) IOSKeyboardColors.textOnAccent() else IOSKeyboardColors.text(isDark)
    val hasAlternates = definition.longPressChars.isNotEmpty()

    Box(
        modifier = Modifier
            .weight(weight)
            .height(IOSKeyboardDimens.keyHeight)
            .scale(scale)
            .zIndex(if (activePressed || showAlternates) 1f else 0f)
            .background(color = backgroundColor, shape = IOSKeyboardShapes.key)
            .then(
                if (hasAlternates) {
                    Modifier.pointerInput(definition.label) {
                        detectTapGestures(
                            onPress = {
                                manualPressed = true
                                runCatching { tryAwaitRelease() }
                                manualPressed = false
                            },
                            onTap = { commit(definition.action) },
                            onLongPress = { showAlternates = true }
                        )
                    }
                } else {
                    Modifier.clickable(interactionSource = interactionSource, indication = null) {
                        commit(definition.action)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        KeyContent(definition = definition, textColor = textColor)

        if (showAlternates && hasAlternates) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -140),
                properties = PopupProperties(focusable = false)
            ) {
                Row(
                    modifier = Modifier
                        .background(IOSKeyboardColors.key(isDark), IOSKeyboardShapes.popup)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    (listOf(definition.label) + definition.longPressChars).forEach { alt ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    showAlternates = false
                                    commit(KeyAction.Character(alt))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = alt, fontSize = 18.sp, color = IOSKeyboardColors.text(isDark))
                        }
                    }
                }
            }
        }
    }
}

/** يعرض أيقونة حقيقية (Vector) للمفاتيح الخاصة بدل الاعتماد على رموز نصية/إيموجي قد لا تظهر بنفس الشكل على كل جهاز */
@Composable
private fun KeyContent(definition: KeyDefinition, textColor: androidx.compose.ui.graphics.Color) {
    val iconRes = when (definition.action) {
        is KeyAction.Shift -> if (definition.label == "⇪") R.drawable.ic_key_shift_locked else R.drawable.ic_key_shift
        KeyAction.Backspace -> R.drawable.ic_key_backspace
        KeyAction.Globe -> R.drawable.ic_key_globe
        KeyAction.Emoji -> R.drawable.ic_key_emoji
        KeyAction.Enter -> R.drawable.ic_key_enter
        else -> null
    }

    if (iconRes != null) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = definition.label,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
    } else {
        Text(
            text = definition.label,
            color = textColor,
            fontSize = if (definition.label.length > 2) 14.sp else 20.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

private fun isSpecialKey(definition: KeyDefinition): Boolean {
    return when (definition.action) {
        KeyAction.Shift, KeyAction.Backspace, KeyAction.Globe, KeyAction.Emoji,
        KeyAction.SwitchToSymbols, KeyAction.SwitchToSymbols2, KeyAction.SwitchToLetters -> true
        else -> false
    }
}
