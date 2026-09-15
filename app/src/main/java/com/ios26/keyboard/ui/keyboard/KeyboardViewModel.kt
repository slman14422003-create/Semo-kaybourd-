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

    // تبدأ اللوحة بحالة Shift مفعّلة تلقائيًا (نفس iOS: أول حرف بأي حقل يطلع كابيتال)
    private val _uiState = MutableStateFlow(KeyboardUiState(shiftState = ShiftState.ON))
    val uiState: StateFlow<KeyboardUiState> = _uiState

    /** آخر محرف تم إرساله فعليًا للحقل، تُستخدم لاكتشاف نمط "مسافة-مسافة" و"نهاية جملة" */
    private var lastCommittedChar: Char? = null

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
                lastCommittedChar = action.char.lastOrNull()
                consumeOneShotShift()
            }
            KeyAction.Space -> handleSpace()
            KeyAction.Enter -> {
                onCommitEnter()
                lastCommittedChar = '\n'
                autoCapitalize()
            }
            KeyAction.Backspace -> {
                onDeleteBackward()
                lastCommittedChar = null
            }
            KeyAction.Shift -> toggleShift()
            KeyAction.SwitchToSymbols -> _uiState.update { it.copy(page = KeyboardPage.SYMBOLS_1) }
            KeyAction.SwitchToSymbols2 -> _uiState.update { it.copy(page = KeyboardPage.SYMBOLS_2) }
            KeyAction.SwitchToLetters -> _uiState.update { it.copy(page = KeyboardPage.LETTERS) }
            KeyAction.Emoji -> _uiState.update { it.copy(page = KeyboardPage.EMOJI) }
            KeyAction.Globe -> onSwitchInputMethod()
        }
    }

    /** مسافة عادية، إلا إذا كانت آخر ضغطة كانت مسافة برضو: يومها نستبدلها بنقطة+مسافة (نمط iOS الكلاسيكي) */
    private fun handleSpace() {
        if (lastCommittedChar == ' ') {
            onDeleteBackward()
            onCommitText(". ")
            lastCommittedChar = ' '
            autoCapitalize()
        } else {
            val previous = lastCommittedChar
            onCommitText(" ")
            lastCommittedChar = ' '
            if (previous == '.' || previous == '!' || previous == '؟' || previous == '?') autoCapitalize()
        }
    }

    private fun autoCapitalize() {
        _uiState.update { if (it.shiftState == ShiftState.OFF) it.copy(shiftState = ShiftState.ON) else it }
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
