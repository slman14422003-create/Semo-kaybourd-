package com.semo.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.logic.AutoCorrect
import com.semo.keyboard.domain.logic.MathEvaluator
import com.semo.keyboard.domain.logic.SuggestionEngine
import com.semo.keyboard.domain.logic.SwipeDecoder
import com.semo.keyboard.domain.logic.WordLists
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.EnterKind
import com.semo.keyboard.domain.model.FieldKind
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.MathResult
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
        val fieldKind: FieldKind = FieldKind.TEXT,
        val alternates: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val toolbarOpen: Boolean = false,
        /** false بحقول كلمات المرور والبريد والروابط: لا اقتراحات ولا تعلّم كلمات */
        val suggestionsAllowed: Boolean = true,
        val recentEmojis: List<String> = emptyList(),
        /** الكلمة الجارية كما كُتبت (تظهر بين علامتي اقتباس) */
        val literal: String = "",
        val mathResult: MathResult? = null,
        /** آخر كلمة كُتبت بالسحب وبدائلها، لاستبدالها باختيار بديل من الشريط */
        val swipeWord: String? = null,
        val swipeAlternates: List<String> = emptyList()
    )

    /** تصحيح تلقائي حصل للتو: الرجوع (Backspace) مباشرة بعده يعيد الكلمة الأصلية */
    private data class Correction(val original: String, val fixed: String)

    private var undo: Correction? = null
    private var lastShiftTap = 0L

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
                soundVolume = s.soundVolume,
                hapticEnabled = s.hapticEnabled,
                numberRow = s.numberRow,
                enterKind = t.enterKind,
                fieldKind = t.fieldKind,
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
                swipeTyping = s.swipeTyping,
                autoCorrect = s.autoCorrect,
                mathResults = s.mathResults,
                suggestions = t.suggestions,
                literal = t.literal,
                mathResult = t.mathResult,
                toolbarOpen = t.toolbarOpen,
                clipItems = clips,
                recentEmojis = t.recentEmojis
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardUiState())

    // ---------- أحداث من الخدمة ----------

    /** يُستدعى عند بدء الكتابة بحقل جديد */
    fun onStartInput(
        page: KeyboardPage,
        capitalize: Boolean,
        enterKind: EnterKind,
        suggestionsAllowed: Boolean,
        fieldKind: FieldKind = FieldKind.TEXT
    ) {
        val autoCap = uiState.value.autoCapitalize
        transient.update {
            it.copy(
                page = page,
                shift = if (capitalize && autoCap) ShiftState.ON else ShiftState.OFF,
                enterKind = enterKind,
                fieldKind = fieldKind,
                alternates = emptyList(),
                suggestions = emptyList(),
                literal = "",
                mathResult = null,
                swipeWord = null,
                swipeAlternates = emptyList(),
                toolbarOpen = false,
                suggestionsAllowed = suggestionsAllowed
            )
        }
        undo = null
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
        val kind = when (action) {
            is KeyAction.Character -> KeyFeedback.STANDARD
            KeyAction.Backspace -> KeyFeedback.DELETE
            else -> KeyFeedback.MODIFIER
        }
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, kind, state.soundVolume)
        if (transient.value.alternates.isNotEmpty()) dismissAlternates()

        // أي مفتاح غير الحذف يلغي إمكانية التراجع عن التصحيح التلقائي
        val pendingUndo = undo
        undo = null
        val swipeWord = transient.value.swipeWord
        if (swipeWord != null && action != KeyAction.Shift) clearSwipe()

        when (action) {
            is KeyAction.Character -> {
                val ch = action.char.first()
                if (!ch.isLetter() && ch != '\'' && ch != '’') {
                    // علامة ترقيم تنهي الكلمة: نصحّح الاختصار أولًا، وإلا نتعلّم الكلمة
                    if (!(ch in AUTOCORRECT_TRIGGERS && applyAutoCorrect() != null)) learnCurrentWord()
                }
                bridge.commitText(action.char)
                consumeOneShotShift()
                refreshSuggestions()
            }
            KeyAction.Space -> {
                val corrected = applyAutoCorrect()
                if (corrected == null) learnCurrentWord() else learnWord(corrected.fixed)
                handleSpace(state.language, state.doubleSpacePeriod)
                undo = corrected
                refreshSuggestions()
            }
            KeyAction.Enter -> {
                val corrected = applyAutoCorrect()
                if (corrected == null) learnCurrentWord() else learnWord(corrected.fixed)
                bridge.performEnter()
                autoCapitalize(state.language)
                refreshSuggestions()
            }
            KeyAction.Backspace -> {
                handleBackspace(pendingUndo, swipeWord)
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
            KeyAction.Mic -> bridge.startVoiceInput()
            KeyAction.None -> Unit
        }
    }

    /**
     * Backspace بنمط iOS: بعد تصحيح تلقائي يعيد الكلمة الأصلية، وبعد كلمة مكتوبة بالسحب يحذفها كاملة،
     * وغير ذلك حذف عادي.
     */
    private fun handleBackspace(pendingUndo: Correction?, swipeWord: String?) {
        if (pendingUndo != null &&
            bridge.textBeforeCursor(pendingUndo.fixed.length + 1) == pendingUndo.fixed + " "
        ) {
            bridge.deleteSurrounding(pendingUndo.fixed.length + 1)
            bridge.commitText(pendingUndo.original)
            return
        }
        if (swipeWord != null && bridge.textBeforeCursor(swipeWord.length + 1) == "$swipeWord ") {
            bridge.deleteSurrounding(swipeWord.length + 1)
            return
        }
        bridge.deleteBackward()
    }

    /** يصحّح الكلمة الجارية لو كانت بجدول التصحيح، ويرجع التصحيح أو null */
    private fun applyAutoCorrect(): Correction? {
        if (!uiState.value.autoCorrect || !transient.value.suggestionsAllowed) return null
        val word = SuggestionEngine.currentWord(bridge.textBeforeCursor(40))
        if (word.isEmpty()) return null
        val fixed = AutoCorrect.fix(word) ?: return null
        bridge.deleteSurrounding(word.length)
        bridge.commitText(fixed)
        return Correction(word, fixed)
    }

    // ---------- الكتابة بالسحب ----------

    /** يُستدعى عند رفع الإصبع بعد سحب على الحروف: [path] الحروف التي مرّ عليها الإصبع بالترتيب */
    fun onSwipeWord(path: List<String>) {
        val state = uiState.value
        val lang = state.language
        val dictionary = if (lang == KeyboardLanguage.ARABIC) WordLists.arabic else WordLists.english
        val learned = learnedWords.value.filter { SuggestionEngine.languageOf(it, lang) == lang }
        val candidates = SwipeDecoder.decode(path, dictionary, learned, limit = 3)
        if (candidates.isEmpty()) return

        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
        undo = null
        val shift = if (lang == KeyboardLanguage.ENGLISH) state.shiftState else ShiftState.OFF
        fun styled(w: String): String = when (shift) {
            ShiftState.OFF -> w
            ShiftState.ON -> w.replaceFirstChar { it.uppercase() }
            ShiftState.LOCKED -> w.uppercase()
        }
        val styledList = candidates.map { styled(it) }
        val word = styledList.first()

        // مسافة قبل الكلمة لو التي قبلها حرف أو رقم
        val last = bridge.textBeforeCursor(1).lastOrNull()
        if (last != null && last.isLetterOrDigit()) bridge.commitText(" ")
        bridge.commitText("$word ")
        consumeOneShotShift()
        learnWord(word)
        transient.update { it.copy(swipeWord = word, swipeAlternates = styledList) }
        refreshSuggestions()
    }

    private fun clearSwipe() {
        transient.update { it.copy(swipeWord = null, swipeAlternates = emptyList()) }
    }

    fun onKeyLongPressed(key: KeyDefinition) {
        if (key.longPressChars.isEmpty()) return
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
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

    /** اختيار اقتراح: يستبدل الكلمة الجارية (أو آخر كلمة كُتبت بالسحب) ويضيف مسافة */
    fun onSuggestionChosen(suggestion: String) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
        undo = null
        val swipeWord = transient.value.swipeWord
        if (swipeWord != null && bridge.textBeforeCursor(swipeWord.length + 1) == "$swipeWord ") {
            bridge.deleteSurrounding(swipeWord.length + 1)
            clearSwipe()
        } else {
            val word = SuggestionEngine.currentWord(bridge.textBeforeCursor(40))
            if (word.isNotEmpty()) bridge.deleteSurrounding(word.length)
        }
        bridge.commitText("$suggestion ")
        learnWord(suggestion)
        consumeOneShotShift()
        refreshSuggestions()
    }

    /** اختيار ناتج العملية الحسابية: يكتب = والناتج (أو الناتج فقط لو المستخدم كتب = بنفسه) */
    fun onMathChosen() {
        val result = transient.value.mathResult ?: return
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
        undo = null
        val before = bridge.textBeforeCursor(3).trimEnd(' ')
        bridge.commitText(if (before.endsWith("=")) result.value else "=" + result.value)
        refreshSuggestions()
    }

    fun onEmojiPressed(emoji: String) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
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
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
        bridge.performEdit(action)
    }

    /** لوحة اللمس (ضغطة مطوّلة على المسافة ثم سحب): dx يمين/يسار، dy أسطر لأعلى/لأسفل */
    fun onCursorMove(dx: Int, dy: Int) {
        if (dx != 0) bridge.moveCursor(dx)
        if (dy != 0) bridge.moveCursorVertical(dy)
    }

    /** بداية وضع لوحة اللمس: اهتزاز خفيف كما بآيفون */
    fun onTrackpadStart() {
        val state = uiState.value
        bridge.keyFeedback(false, state.hapticEnabled)
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

    /** ضغطة = تشغيل/إيقاف، ضغطتان سريعتان = قفل الأحرف الكبيرة (مثل آيفون) */
    private fun toggleShift() {
        val now = System.currentTimeMillis()
        val quick = now - lastShiftTap < DOUBLE_TAP_MS
        lastShiftTap = now
        transient.update {
            it.copy(
                shift = when (it.shift) {
                    ShiftState.OFF -> ShiftState.ON
                    ShiftState.ON -> if (quick) ShiftState.LOCKED else ShiftState.OFF
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

    private fun setSuggestions(list: List<String>, literal: String, math: MathResult?) {
        transient.update {
            if (it.suggestions == list && it.literal == literal && it.mathResult == math) it
            else it.copy(suggestions = list, literal = literal, mathResult = math)
        }
    }

    private fun refreshSuggestions() {
        val state = uiState.value
        val t = transient.value
        if (!t.suggestionsAllowed || (!state.suggestionsEnabled && !state.mathResults)) {
            setSuggestions(emptyList(), "", null)
            return
        }
        val before = bridge.textBeforeCursor(80)

        // ناتج العملية الحسابية له الأولوية بالشريط
        if (state.mathResults) {
            val math = MathEvaluator.detect(before)
            if (math != null) {
                setSuggestions(emptyList(), "", math)
                return
            }
        }
        if (!state.suggestionsEnabled) {
            setSuggestions(emptyList(), "", null)
            return
        }

        // بدائل آخر كلمة كُتبت بالسحب تبقى ظاهرة طالما المؤشر بعدها مباشرة
        val swipe = t.swipeWord
        if (swipe != null) {
            if (before.endsWith("$swipe ")) {
                setSuggestions(t.swipeAlternates, "", null)
                return
            }
            clearSwipe()
        }

        val word = SuggestionEngine.currentWord(before)
        if (word.isEmpty()) {
            val trimmed = before.trimEnd()
            val atStart = trimmed.isEmpty() || trimmed.last() in SENTENCE_END
            setSuggestions(if (atStart) SuggestionEngine.starters(state.language) else emptyList(), "", null)
        } else {
            val language = SuggestionEngine.languageOf(word, state.language)
            val completions = SuggestionEngine.suggest(word, language, learnedWords.value, limit = 2)
                .filter { !it.equals(word, ignoreCase = true) }
            setSuggestions(completions, word, null)
        }
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '؟')
        /** علامات الترقيم التي تُنهي الكلمة وتشغّل التصحيح التلقائي */
        const val AUTOCORRECT_TRIGGERS = ".,!?;:)\"؟،"
        const val MAX_RECENT_EMOJIS = 24
        const val DOUBLE_TAP_MS = 350L
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
