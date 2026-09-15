package com.ios26.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ios26.keyboard.data.SettingsRepository
import com.ios26.keyboard.domain.model.KeyAction
import com.ios26.keyboard.domain.model.KeyboardPage
import com.ios26.keyboard.domain.model.KeyboardUiState
import com.ios26.keyboard.domain.model.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * يحمل منطق الحالة فقط (ما هي الصفحة الحالية، حالة Shift، التفضيلات)،
 * ويترك تنفيذ فعل الإدخال الفعلي (الكتابة بالحقل) لخدمة الـ IME عبر callback.
 */
class KeyboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val onCommitText: (String) -> Unit,
    private val onDeleteBackward: () -> Unit,
    private val onCommitEnter: () -> Unit,
    private val onSwitchInputMethod: () -> Unit
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState

    init {
        combine(
            settingsRepository.isDarkTheme,
            settingsRepository.isSoundEnabled,
            settingsRepository.isHapticEnabled
        ) { dark, sound, haptic -> Triple(dark, sound, haptic) }
            .onEach { (dark, sound, haptic) ->
                _uiState.update { it.copy(isDarkTheme = dark, soundEnabled = sound, hapticEnabled = haptic) }
            }
            .launchIn(viewModelScope)
    }

    fun onKeyPressed(action: KeyAction) {
        when (action) {
            is KeyAction.Character -> {
                onCommitText(action.char)
                consumeOneShotShift()
            }
            KeyAction.Space -> onCommitText(" ")
            KeyAction.Enter -> onCommitEnter()
            KeyAction.Backspace -> onDeleteBackward()
            KeyAction.Shift -> toggleShift()
            KeyAction.SwitchToSymbols -> _uiState.update { it.copy(page = KeyboardPage.SYMBOLS_1) }
            KeyAction.SwitchToSymbols2 -> _uiState.update { it.copy(page = KeyboardPage.SYMBOLS_2) }
            KeyAction.SwitchToLetters -> _uiState.update { it.copy(page = KeyboardPage.LETTERS) }
            KeyAction.Emoji -> _uiState.update { it.copy(page = KeyboardPage.EMOJI) }
            KeyAction.Globe -> onSwitchInputMethod()
        }
    }

    private fun toggleShift() {
        _uiState.update { state ->
            val next = when (state.shiftState) {
                ShiftState.OFF -> ShiftState.ON
                ShiftState.ON -> ShiftState.LOCKED
                ShiftState.LOCKED -> ShiftState.OFF
            }
            state.copy(shiftState = next)
        }
    }

    /** بعد كتابة حرف واحد بحالة Shift مؤقتة (ON)، ترجع اللوحة تلقائيًا لحالة الأحرف الصغيرة */
    private fun consumeOneShotShift() {
        _uiState.update { if (it.shiftState == ShiftState.ON) it.copy(shiftState = ShiftState.OFF) else it }
    }
}
