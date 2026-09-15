package com.ios26.keyboard.domain.model

/** أنواع المفاتيح المدعومة داخل اللوحة */
sealed class KeyAction {
    data class Character(val char: String) : KeyAction()
    object Shift : KeyAction()
    object Backspace : KeyAction()
    object Space : KeyAction()
    object Enter : KeyAction()
    object SwitchToSymbols : KeyAction()
    object SwitchToLetters : KeyAction()
    object SwitchToSymbols2 : KeyAction()
    object Emoji : KeyAction()
    object Globe : KeyAction()
}

/** تعريف مفتاح واحد على اللوحة، مع وزن العرض النسبي */
data class KeyDefinition(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val isAccent: Boolean = false,
    /** بدائل تظهر بفقاعة عند الضغط المطوّل (مثل a → à á â ã ä å)، متل لوحة iOS */
    val longPressChars: List<String> = emptyList()
)

enum class ShiftState { OFF, ON, LOCKED }

enum class KeyboardPage { LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI }

data class KeyboardUiState(
    val page: KeyboardPage = KeyboardPage.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val isDarkTheme: Boolean = false,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true
)
