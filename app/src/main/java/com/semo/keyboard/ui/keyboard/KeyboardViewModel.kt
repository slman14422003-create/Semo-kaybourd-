package com.semo.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** منطق الحالة فقط؛ الكتابة الفعلية بالحقل تتم عبر [InputBridge]. */
class KeyboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val bridge: InputBridge
) : ViewModel() {

    private data class LocalState(
        val page: KeyboardPage = KeyboardPage.LETTERS,
        val shift: ShiftState = ShiftState.OFF,
        val enterKind: EnterKind = EnterKind.RETURN,
        val alternates: List<String> = emptyList()
    )

    private val transient = MutableStateFlow(LocalState())

    val uiState: StateFlow<KeyboardUiState> =
        combine(transient, settingsRepository.settings) { t, s: SemoSettings ->
            KeyboardUiState(
                page = t.page,
                shiftState = if (s.language == KeyboardLanguage.ENGLISH) t.shift else ShiftState.OFF,
                language = s.language,
                themeMode = s.themeMode,
                soundEnabled = s.soundEnabled,
                hapticEnabled = s.hapticEnabled,
                numberRow = s.numberRow,
                enterKind = t.enterKind,
                alternates = t.alternates
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardUiState())

    // ---------- أحداث من الخدمة ----------

    /** يُستدعى عند بدء الكتابة بحقل جديد */
    fun onStartInput(page: KeyboardPage, capitalize: Boolean, enterKind: EnterKind) {
        transient.value = LocalState(
            page = page,
            shift = if (capitalize) ShiftState.ON else ShiftState.OFF,
            enterKind = enterKind
        )
    }

    /** يُستدعى عند تحرك المؤشر: لو صرنا ببداية جملة نفعّل Shift تلقائيًا */
    fun onCursorMoved(capitalize: Boolean) {
        if (capitalize) transient.update { if (it.shift == ShiftState.OFF) it.copy(shift = ShiftState.ON) else it }
    }

    // ---------- أحداث من الواجهة ----------

    fun onKeyPressed(action: KeyAction) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled)
        if (transient.value.alternates.isNotEmpty()) dismissAlternates()

        when (action) {
            is KeyAction.Character -> {
                bridge.commitText(action.char)
                consumeOneShotShift()
            }
            KeyAction.Space -> handleSpace(state.language)
            KeyAction.Enter -> {
                bridge.performEnter()
                autoCapitalize(state.language)
            }
            KeyAction.Backspace -> bridge.deleteBackward()
            KeyAction.Shift -> toggleShift()
            KeyAction.SwitchToSymbols -> setPage(KeyboardPage.SYMBOLS_1)
            KeyAction.SwitchToSymbols2 -> setPage(KeyboardPage.SYMBOLS_2)
            KeyAction.SwitchToLetters -> setPage(KeyboardPage.LETTERS)
            KeyAction.Emoji -> setPage(KeyboardPage.EMOJI)
            KeyAction.SwitchLanguage -> {
                val next = if (state.language == KeyboardLanguage.ENGLISH) KeyboardLanguage.ARABIC else KeyboardLanguage.ENGLISH
                viewModelScope.launch { settingsRepository.setLanguage(next) }
            }
            KeyAction.Globe -> bridge.switchKeyboard()
            KeyAction.Hide -> bridge.hideKeyboard()
            KeyAction.None -> Unit
        }
    }

    fun onKeyLongPressed(key: KeyDefinition) {
        if (key.longPressChars.isEmpty()) return
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled)
        transient.update { it.copy(alternates = key.longPressChars) }
    }

    fun onAlternateChosen(char: String) {
        bridge.commitText(char)
        dismissAlternates()
        consumeOneShotShift()
    }

    fun dismissAlternates() {
        transient.update { it.copy(alternates = emptyList()) }
    }

    // ---------- داخلي ----------

    private fun setPage(page: KeyboardPage) = transient.update { it.copy(page = page) }

    /** مسافتان متتاليتان بعد حرف/رقم = نقطة + مسافة (نمط iOS) */
    private fun handleSpace(language: KeyboardLanguage) {
        val before = bridge.textBeforeCursor(2)
        if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            bridge.deleteBackward()
            bridge.commitText(". ")
            autoCapitalize(language)
        } else {
            bridge.commitText(" ")
            val last = before.lastOrNull()
            if (last != null && last in SENTENCE_END) autoCapitalize(language)
        }
    }

    private fun autoCapitalize(language: KeyboardLanguage) {
        if (language != KeyboardLanguage.ENGLISH) return
        transient.update { if (it.shift == ShiftState.OFF) it.copy(shift = ShiftState.ON) else it }
    }

    private fun toggleShift() {
        transient.update {
            it.copy(
                shift = when (it.shift) {
                    ShiftState.OFF -> ShiftState.ON
                    ShiftState.ON -> ShiftState.LOCKED
                    ShiftState.LOCKED -> ShiftState.OFF
                }
            )
        }
    }

    private fun consumeOneShotShift() {
        transient.update { if (it.shift == ShiftState.ON) it.copy(shift = ShiftState.OFF) else it }
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '؟')
    }
}

class KeyboardViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val bridge: InputBridge
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KeyboardViewModel(settingsRepository, bridge) as T
}
