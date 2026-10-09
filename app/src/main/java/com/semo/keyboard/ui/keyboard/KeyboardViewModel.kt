package com.semo.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.logic.SuggestionEngine
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.OneHandMode
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
    private val clipboardRepository: ClipboardRepository,
    private val learnedWordsRepository: LearnedWordsRepository,
    private val bridge: InputBridge
) : ViewModel() {

    private data class LocalState(
        val page: KeyboardPage = KeyboardPage.LETTERS,
        val shift: ShiftState = ShiftState.OFF,
        val enterKind: EnterKind = EnterKind.RETURN,
        val alternates: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val toolbarOpen: Boolean = false,
        /** false بحقول كلمات المرور والبريد والروابط: لا اقتراحات ولا تعلّم كلمات */
        val suggestionsAllowed: Boolean = true,
        val recentEmojis: List<String> = emptyList()
    )

    private val transient = MutableStateFlow(LocalState())

    private val learnedWords: StateFlow<List<String>> =
        learnedWordsRepository.words.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<KeyboardUiState> =
        combine(
            transient,
            settingsRepository.settings,
            clipboardRepository.items
        ) { t: LocalState, s: SemoSettings, clips: List<ClipItem> ->
            KeyboardUiState(
                page = t.page,
                shiftState = if (s.language == KeyboardLanguage.ENGLISH) t.shift else ShiftState.OFF,
                language = s.language,
                themeMode = s.themeMode,
                soundEnabled = s.soundEnabled,
                hapticEnabled = s.hapticEnabled,
                numberRow = s.numberRow,
                enterKind = t.enterKind,
                alternates = t.alternates,
                englishLayout = s.englishLayout,
                arabicLayout = s.arabicLayout,
                style = s.style,
                size = s.size,
                oneHand = s.oneHand,
                suggestionsEnabled = s.suggestionsEnabled,
                keyPreview = s.keyPreview,
                autoCapitalize = s.autoCapitalize,
                doubleSpacePeriod = s.doubleSpacePeriod,
                clipboardEnabled = s.clipboardEnabled,
                arabicDigits = s.arabicDigits,
                suggestions = t.suggestions,
                toolbarOpen = t.toolbarOpen,
                clipItems = clips,
                recentEmojis = t.recentEmojis
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardUiState())

    // ---------- أحداث من الخدمة ----------

    /** يُستدعى عند بدء الكتابة بحقل جديد */
    fun onStartInput(page: KeyboardPage, capitalize: Boolean, enterKind: EnterKind, suggestionsAllowed: Boolean) {
        val autoCap = uiState.value.autoCapitalize
        transient.update {
            it.copy(
                page = page,
                shift = if (capitalize && autoCap) ShiftState.ON else ShiftState.OFF,
                enterKind = enterKind,
                alternates = emptyList(),
                suggestions = emptyList(),
                toolbarOpen = false,
                suggestionsAllowed = suggestionsAllowed
            )
        }
        refreshSuggestions()
    }

    /** يُستدعى عند تحرك المؤشر: لو صرنا ببداية جملة نفعّل Shift تلقائيًا */
    fun onCursorMoved(capitalize: Boolean) {
        if (capitalize && uiState.value.autoCapitalize) {
            transient.update { if (it.shift == ShiftState.OFF) it.copy(shift = ShiftState.ON) else it }
        }
        refreshSuggestions()
    }

    // ---------- أحداث من الواجهة ----------

    fun onKeyPressed(action: KeyAction) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled)
        if (transient.value.alternates.isNotEmpty()) dismissAlternates()

        when (action) {
            is KeyAction.Character -> {
                // أي رمز غير حرف (نقطة، فاصلة...) يُنهي الكلمة فنتعلّمها
                if (!action.char.first().isLetter()) learnCurrentWord()
                bridge.commitText(action.char)
                consumeOneShotShift()
                refreshSuggestions()
            }
            KeyAction.Space -> {
                learnCurrentWord()
                handleSpace(state.language, state.doubleSpacePeriod)
                refreshSuggestions()
            }
            KeyAction.Enter -> {
                learnCurrentWord()
                bridge.performEnter()
                autoCapitalize(state.language)
                refreshSuggestions()
            }
            KeyAction.Backspace -> {
                bridge.deleteBackward()
                refreshSuggestions()
            }
            KeyAction.Shift -> toggleShift()
            KeyAction.SwitchToSymbols -> setPage(KeyboardPage.SYMBOLS_1)
            KeyAction.SwitchToSymbols2 -> setPage(KeyboardPage.SYMBOLS_2)
            KeyAction.SwitchToLetters -> setPage(KeyboardPage.LETTERS)
            KeyAction.Emoji -> setPage(KeyboardPage.EMOJI)
            KeyAction.SwitchLanguage -> {
                val next = if (state.language == KeyboardLanguage.ENGLISH) KeyboardLanguage.ARABIC else KeyboardLanguage.ENGLISH
                setPage(KeyboardPage.LETTERS)
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
        refreshSuggestions()
    }

    fun dismissAlternates() {
        transient.update { it.copy(alternates = emptyList()) }
    }

    /** اختيار اقتراح: يستبدل الكلمة الجارية ويضيف مسافة */
    fun onSuggestionChosen(suggestion: String) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled)
        val word = SuggestionEngine.currentWord(bridge.textBeforeCursor(40))
        if (word.isNotEmpty()) bridge.deleteSurrounding(word.length)
        bridge.commitText("$suggestion ")
        learnWord(suggestion)
        consumeOneShotShift()
        refreshSuggestions()
    }

    fun onEmojiPressed(emoji: String) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled)
        bridge.commitText(emoji)
        transient.update { t ->
            t.copy(recentEmojis = (listOf(emoji) + t.recentEmojis.filter { it != emoji }).take(MAX_RECENT_EMOJIS))
        }
    }

    // ---------- شريط الأدوات والصفحات ----------

    fun toggleToolbar() {
        transient.update { it.copy(toolbarOpen = !it.toolbarOpen) }
    }

    fun openPage(page: KeyboardPage) {
        transient.update { it.copy(page = page, alternates = emptyList()) }
    }

    fun cycleOneHand() {
        val next = when (uiState.value.oneHand) {
            OneHandMode.OFF -> OneHandMode.RIGHT
            OneHandMode.RIGHT -> OneHandMode.LEFT
            OneHandMode.LEFT -> OneHandMode.OFF
        }
        viewModelScope.launch { settingsRepository.setOneHand(next) }
    }

    fun setOneHand(mode: OneHandMode) {
        viewModelScope.launch { settingsRepository.setOneHand(mode) }
    }

    fun openSettings() = bridge.openSettings()

    // ---------- الحافظة والتحرير ----------

    fun onClipPaste(text: String) {
        bridge.commitText(text)
        refreshSuggestions()
    }

    fun onClipTogglePin(text: String) {
        viewModelScope.launch { clipboardRepository.togglePin(text) }
    }

    fun onClipDelete(text: String) {
        viewModelScope.launch { clipboardRepository.remove(text) }
    }

    fun onClipClearUnpinned() {
        viewModelScope.launch { clipboardRepository.clearUnpinned() }
    }

    fun onEditAction(action: EditAction) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled)
        bridge.performEdit(action)
    }

    /** سحب أفقي على شريط المسافة */
    fun onCursorMove(delta: Int) {
        if (delta == 0) return
        bridge.moveCursor(delta)
    }

    // ---------- داخلي ----------

    private fun setPage(page: KeyboardPage) = transient.update { it.copy(page = page) }

    /** مسافتان متتاليتان بعد حرف/رقم = نقطة + مسافة (نمط iOS) */
    private fun handleSpace(language: KeyboardLanguage, doubleSpacePeriod: Boolean) {
        val before = bridge.textBeforeCursor(2)
        if (doubleSpacePeriod && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
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
        if (language != KeyboardLanguage.ENGLISH || !uiState.value.autoCapitalize) return
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

    private fun canLearn(): Boolean = uiState.value.suggestionsEnabled && transient.value.suggestionsAllowed

    private fun learnCurrentWord() {
        if (!canLearn()) return
        learnWord(SuggestionEngine.currentWord(bridge.textBeforeCursor(40)))
    }

    private fun learnWord(word: String) {
        if (!canLearn() || word.length < 3) return
        val normalized = if (word.firstOrNull()?.code?.let { it < 0x0600 } == true) word.lowercase() else word
        viewModelScope.launch { learnedWordsRepository.learn(normalized) }
    }

    private fun refreshSuggestions() {
        val state = uiState.value
        if (!state.suggestionsEnabled || !transient.value.suggestionsAllowed) {
            if (transient.value.suggestions.isNotEmpty()) transient.update { it.copy(suggestions = emptyList()) }
            return
        }
        val before = bridge.textBeforeCursor(40)
        val word = SuggestionEngine.currentWord(before)
        val list = if (word.isEmpty()) {
            val trimmed = before.trimEnd()
            val atStart = trimmed.isEmpty() || trimmed.last() in SENTENCE_END
            if (atStart) SuggestionEngine.starters(state.language) else emptyList()
        } else {
            SuggestionEngine.suggest(word, SuggestionEngine.languageOf(word, state.language), learnedWords.value)
        }
        transient.update { if (it.suggestions == list) it else it.copy(suggestions = list) }
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '؟')
        const val MAX_RECENT_EMOJIS = 24
    }
}

class KeyboardViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val clipboardRepository: ClipboardRepository,
    private val learnedWordsRepository: LearnedWordsRepository,
    private val bridge: InputBridge
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KeyboardViewModel(settingsRepository, clipboardRepository, learnedWordsRepository, bridge) as T
}
