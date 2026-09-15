package com.ios26.keyboard.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ios26.keyboard.domain.logic.KeyboardLayoutProvider
import com.ios26.keyboard.domain.model.KeyDefinition
import com.ios26.keyboard.domain.model.KeyboardUiState
import com.ios26.keyboard.ui.theme.IOSKeyboardColors
import com.ios26.keyboard.ui.theme.IOSKeyboardDimens
import com.ios26.keyboard.ui.theme.IOSKeyboardShapes

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
    onKeyPressed: (com.ios26.keyboard.domain.model.KeyAction) -> Unit
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
                onClick = { onKeyPressed(key.action) }
            )
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    definition: KeyDefinition,
    isDark: Boolean,
    weight: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 60),
        label = "keyScale"
    )

    val backgroundColor = when {
        definition.isAccent -> IOSKeyboardColors.keyAccent(isDark)
        isSpecialKey(definition) -> IOSKeyboardColors.keySpecial(isDark)
        else -> IOSKeyboardColors.key(isDark)
    }
    val textColor = if (definition.isAccent) IOSKeyboardColors.textOnAccent() else IOSKeyboardColors.text(isDark)

    Box(
        modifier = Modifier
            .weight(weight)
            .height(IOSKeyboardDimens.keyHeight)
            .scale(scale)
            .zIndex(if (isPressed) 1f else 0f)
            .background(color = backgroundColor, shape = IOSKeyboardShapes.key)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = definition.label,
            color = textColor,
            fontSize = if (definition.label.length > 2) 14.sp else 20.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

private fun isSpecialKey(definition: KeyDefinition): Boolean {
    val label = definition.label
    return label in setOf("⇧", "⇪", "⌫", "123", "ABC", "#+=", "🌐", "😊", "مسافة")
}
