package com.semo.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.ShortcutsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.logic.AutoCorrect
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.logic.NextWordModel
import com.semo.keyboard.domain.logic.EmojiSuggestions
import com.semo.keyboard.domain.logic.MathEvaluator
import com.semo.keyboard.domain.logic.SuggestionEngine
import com.semo.keyboard.domain.logic.SwipeDecoder
import com.semo.keyboard.domain.logic.WordLists
import com.semo.keyboard.domain.model.clipImageMime
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isClipLong
import com.semo.keyboard.domain.model.NumKeyTarget
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
import com.semo.keyboard.domain.model.TextShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** منطق الحالة فقط؛ الكتابة الفعلية بالحقل تتم عبر [InputBridge]. */
class KeyboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val clipboardRepository: ClipboardRepository,
    private val learnedWordsRepository: LearnedWordsRepository,
    private val shortcutsRepository: ShortcutsRepository,
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
        val swipeAlternates: List<String> = emptyList(),
        /** نص نُسخ للتو (شريحة اللصق السريع بشريط الاقتراحات) */
        val freshClip: String? = null
    )

    /** تصحيح تلقائي حصل للتو: الرجوع (Backspace) مباشرة بعده يعيد الكلمة الأصلية */
    private data class Correction(val original: String, val fixed: String)

    private var undo: Correction? = null
    private var lastShiftTap = 0L

    private val transient = MutableStateFlow(LocalState())

    private val learnedWords: StateFlow<List<String>> =
        learnedWordsRepository.words.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** الكلمات المتعلَّمة بصيغة مفتاح البحث (للتدقيق الإملائي: الكلمة المتعلَّمة تُعدّ صحيحة) */
    private val learnedKeys: StateFlow<Set<String>> =
        learnedWordsRepository.words
            .map { list ->
                val keys: Set<String> = list.mapTo(HashSet<String>()) { BigDictionary.key(it) }
                keys
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** أزواج الكلمات المتعلَّمة لتوقّع الكلمة التالية */
    private val learnedBigrams: StateFlow<Map<String, List<String>>> =
        learnedWordsRepository.bigrams.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val shortcuts: StateFlow<List<TextShortcut>> =
        shortcutsRepository.shortcuts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                keySpacing = s.keySpacing,
                numKeyTarget = s.numKeyTarget,
                toolbarItems = s.toolbarItems,
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
                globeOffsetDp = s.globeOffsetDp,
                hideButtonMode = s.hideButtonMode,
                bottomRaiseDp = s.bottomRaiseDp,
                suggestions = t.suggestions,
                literal = t.literal,
                mathResult = t.mathResult,
                toolbarOpen = t.toolbarOpen,
                clipItems = clips,
                nextWord = s.nextWord,
                spellCheck = s.spellCheck,
                suggestEmojis = s.suggestEmojis,
                textShortcuts = s.textShortcuts,
                holdDelay = s.holdDelay,
                backspaceSpeed = s.backspaceSpeed,
                spaceBarAction = s.spaceBarAction,
                keepSymbolPanel = s.keepSymbolPanel,
                recentEmojis = s.recentEmojis,
                freshClip = t.freshClip
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
        // اتصال الحقل قد لا يكون جاهزًا لحظة فتح اللوحة (يرجع نصًا فارغًا) فيبقى الشريط فارغًا: نعيد التحديث بعد لحظة
        viewModelScope.launch {
            delay(300)
            refreshSuggestions()
        }
    }

    /** يُستدعى عند تحرك المؤشر: لو صرنا ببداية جملة نفعّل Shift تلقائيًا */
    fun onCursorMoved(capitalize: Boolean) {
        if (capitalize && uiState.value.autoCapitalize) {
            transient.update { if (it.shift == ShiftState.OFF) it.copy(shift = ShiftState.ON) else it }
        }
        refreshSuggestions()
    }

    // ---------- أحداث من الواجهة ----------

    /** ضغطة مع صوت واهتزاز (الحذف المتكرر، الضغطات المطوّلة، وغيرها) */
    fun onKeyPressed(action: KeyAction) = handleKey(action, feedback = true)

    /** ضغطة تأكيد عند رفع الإصبع: الصوت والاهتزاز يكونان قد حصلا لحظة اللمس عبر [onKeyDown] */
    fun onKeyTapped(action: KeyAction) = handleKey(action, feedback = false)

    /**
     * صوت واهتزاز لحظة لمس المفتاح (مثل آيفون)، لا لحظة رفع الإصبع: كان الاهتزاز يتأخر حتى الرفع
     * فيبدو الضغط "متأخرًا ومقطّعًا"، والآن الإحساس فوري وسلس.
     */
    fun onKeyDown(action: KeyAction) {
        val state = uiState.value
        if (!state.soundEnabled && !state.hapticEnabled) return
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, feedbackKind(action), state.soundVolume)
    }

    /** نقرة خفيفة بلا صوت (تبديل فئة، لمس عنصر بالواجهة) */
    fun onSelectionTick() {
        val state = uiState.value
        if (state.hapticEnabled) bridge.keyFeedback(false, true, KeyFeedback.SELECTION)
    }

    private fun feedbackKind(action: KeyAction): KeyFeedback = when (action) {
        is KeyAction.Character -> KeyFeedback.STANDARD
        KeyAction.Backspace -> KeyFeedback.DELETE
        else -> KeyFeedback.MODIFIER
    }

    private fun handleKey(action: KeyAction, feedback: Boolean) {
        val state = uiState.value
        if (feedback) {
            bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, feedbackKind(action), state.soundVolume)
        }
        if (transient.value.alternates.isNotEmpty()) dismissAlternates()

        // أي مفتاح غير الحذف يلغي إمكانية التراجع عن التصحيح التلقائي
        val pendingUndo = undo
        undo = null
        val swipeWord = transient.value.swipeWord
        if (swipeWord != null && action != KeyAction.Shift) clearSwipe()

        when (action) {
            is KeyAction.Character -> {
                if (transient.value.freshClip != null) transient.update { it.copy(freshClip = null) }
                val ch = action.char.first()
                // الأرقام ورموز العمليات لا تُنهي كلمة: نتجنّب قراءة النص من التطبيق (استدعاء بين عمليتين) بكل ضغطة رقم
                if (!ch.isLetter() && !ch.isDigit() && ch != '\'' && ch != '’' && ch !in NON_WORD_END_SYMBOLS) {
                    // علامة ترقيم تنهي الكلمة: نصحّح الاختصار أولًا، وإلا نتعلّم الكلمة (قراءة واحدة من التطبيق)
                    val before = bridge.textBeforeCursor(40)
                    if (!(ch in AUTOCORRECT_TRIGGERS && applyAutoCorrect(before) != null)) learnWord(SuggestionEngine.currentWord(before))
                }
                bridge.commitText(action.char)
                consumeOneShotShift()
                // «إبقاء لوحة الرموز مفتوحة» متوقف: بعد كتابة رمز نرجع لصفحة الحروف
                val page = transient.value.page
                if (!state.keepSymbolPanel && (page == KeyboardPage.SYMBOLS_1 || page == KeyboardPage.SYMBOLS_2)) {
                    setPage(KeyboardPage.LETTERS)
                }
                refreshSuggestions()
            }
            KeyAction.Space -> {
                // قراءة واحدة من التطبيق بدل ثلاث (كل قراءة استدعاء بين عمليتين قد يتأخر)
                val before = bridge.textBeforeCursor(40)
                val corrected = applyAutoCorrect(before)
                val finalWord = corrected?.fixed ?: SuggestionEngine.currentWord(before)
                learnWord(finalWord)
                learnPair(before, corrected?.original ?: finalWord, finalWord)
                val tail = if (corrected == null) before else before.dropLast(corrected.original.length) + corrected.fixed
                handleSpace(state.language, state.doubleSpacePeriod, tail.takeLast(2))
                undo = corrected
                refreshSuggestions()
            }
            KeyAction.Enter -> {
                val before = bridge.textBeforeCursor(40)
                val corrected = applyAutoCorrect(before)
                val finalWord = corrected?.fixed ?: SuggestionEngine.currentWord(before)
                learnWord(finalWord)
                learnPair(before, corrected?.original ?: finalWord, finalWord)
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
            KeyAction.SwitchToNumbers ->
                setPage(if (state.numKeyTarget == NumKeyTarget.NUMPAD) KeyboardPage.NUMPAD else KeyboardPage.SYMBOLS_1)
            KeyAction.SwitchToNumpad -> setPage(KeyboardPage.NUMPAD)
            KeyAction.Emoji -> setPage(KeyboardPage.EMOJI)
            KeyAction.SwitchLanguage -> {
                val next = if (state.language == KeyboardLanguage.ENGLISH) KeyboardLanguage.ARABIC else KeyboardLanguage.ENGLISH
                setPage(KeyboardPage.LETTERS)
                viewModelScope.launch { settingsRepository.setLanguage(next) }
            }
            KeyAction.Globe -> bridge.switchKeyboard()
            KeyAction.Mic -> bridge.startVoiceInput()
            KeyAction.HideKeyboard -> bridge.hideKeyboard()
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

    /**
     * يستبدل الكلمة الجارية عند إنهائها (مسافة/ترقيم) ويرجع الاستبدال أو null. الأولوية:
     * اختصار نصي من المستخدم، ثم جدول التصحيح (اختصارات وهمزات)، ثم التصحيح الإملائي الحذر.
     */
    private fun applyAutoCorrect(before: String): Correction? {
        if (!transient.value.suggestionsAllowed) return null
        val state = uiState.value
        val word = SuggestionEngine.currentWord(before)
        if (word.isEmpty()) return null
        var fixed: String? = null
        if (state.textShortcuts) {
            fixed = shortcuts.value.firstOrNull { it.abbr.equals(word, ignoreCase = true) }?.expansion
        }
        if (fixed == null && state.autoCorrect) {
            fixed = AutoCorrect.fix(word)
            if (fixed == null) {
                val language = SuggestionEngine.languageOf(word, state.language)
                fixed = SuggestionEngine.smartFix(word, language, learnedKeys.value)
            }
        }
        val result = fixed ?: return null
        if (result == word) return null
        bridge.deleteSurrounding(word.length)
        bridge.commitText(result)
        return Correction(word, result)
    }

    // ---------- الكتابة بالسحب ----------

    /** يُستدعى عند رفع الإصبع بعد سحب على الحروف: [path] الحروف التي مرّ عليها الإصبع بالترتيب */
    fun onSwipeWord(path: List<String>) {
        val state = uiState.value
        val lang = state.language
        val dictionary = if (lang == KeyboardLanguage.ARABIC) WordLists.arabic else WordLists.english
        val learned = learnedWords.value.filter { SuggestionEngine.languageOf(it, lang) == lang }
        // فك المسار على خيط خلفي: مع القاموس الكبير قد يأخذ عشرات الميلي ثانية
        viewModelScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                SwipeDecoder.decode(path, dictionary, learned, limit = 3, language = lang)
            }
            if (candidates.isNotEmpty()) commitSwipe(candidates)
        }
    }

    private fun commitSwipe(candidates: List<String>) {
        val state = uiState.value
        val lang = state.language
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
        bridge.keyFeedback(false, state.hapticEnabled, KeyFeedback.LONG_PRESS)
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
            val before = bridge.textBeforeCursor(40)
            val word = SuggestionEngine.currentWord(before)
            if (word.isNotEmpty()) bridge.deleteSurrounding(word.length)
            learnPair(before, word, suggestion)
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
        val updated = (listOf(emoji) + state.recentEmojis.filter { it != emoji }).take(MAX_RECENT_EMOJIS)
        viewModelScope.launch { settingsRepository.setRecentEmojis(updated) }
    }

    // ---------- شريحة اللصق السريع (نص نُسخ للتو) ----------

    private var freshClipJob: Job? = null

    /**
     * تُستدعى من الخدمة عند نسخ شيء جديد: تظهر شريحة لصق سريعة بالشريط لمدة 90 ثانية.
     * [stored] هو المفتاح المخزَّن بسجل الحافظة (نص، رابط، كود، مفتاح صورة أو نص طويل)،
     * فيُلصق مباشرة بدون البحث عنه بالسجل.
     */
    fun onClipboardCopied(stored: String) {
        if (stored.isBlank()) return
        transient.update { it.copy(freshClip = stored) }
        freshClipJob?.cancel()
        freshClipJob = viewModelScope.launch {
            delay(FRESH_CLIP_MS)
            transient.update { if (it.freshClip == stored) it.copy(freshClip = null) else it }
        }
    }

    fun onFreshClipPaste() {
        val stored = transient.value.freshClip ?: return
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, KeyFeedback.MODIFIER, state.soundVolume)
        transient.update { it.copy(freshClip = null) }
        onClipPaste(stored)
    }

    fun dismissFreshClip() {
        transient.update { it.copy(freshClip = null) }
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
        when {
            text.isClipImage() -> bridge.commitImage(text.clipImageName(), text.clipImageMime())
            text.isClipLong() -> viewModelScope.launch {
                // النص الطويل (كود مثلًا) محفوظ بملف: نقرأه بخيط خلفي ثم نلصقه كاملًا
                val full = clipboardRepository.fullText(text)
                if (!full.isNullOrEmpty()) {
                    bridge.commitText(full)
                    refreshSuggestions()
                }
            }
            else -> {
                bridge.commitText(text)
                refreshSuggestions()
            }
        }
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
        bridge.keyFeedback(false, state.hapticEnabled, KeyFeedback.LONG_PRESS)
    }

    // ---------- داخلي ----------

    private fun setPage(page: KeyboardPage) = transient.update { it.copy(page = page) }

    /** مسافتان متتاليتان بعد حرف/رقم = نقطة + مسافة (نمط iOS) */
    private fun handleSpace(language: KeyboardLanguage, doubleSpacePeriod: Boolean, before: String) {
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

    private fun normalizeForLearning(word: String): String =
        if (word.firstOrNull()?.code?.let { it < 0x0600 } == true) word.lowercase() else word

    private fun learnWord(word: String) {
        if (!canLearn() || word.length < 3) return
        val normalized = normalizeForLearning(word)
        viewModelScope.launch { learnedWordsRepository.learn(normalized) }
    }

    /**
     * يتعلّم زوج (الكلمة السابقة ← [word]) لتوقّع الكلمة التالية. [before] نص قبل المؤشر، و[typed] الكلمة
     * الجارية فيه (قد تكون فارغة عند اختيار توقّع). لا نتعلّم عبر علامات الترقيم أو الأسطر.
     */
    private fun learnPair(before: String, typed: String, word: String) {
        if (!canLearn() || word.length < 2) return
        val rest = before.dropLast(typed.length)
        if (!rest.endsWith(' ')) return
        val prev = SuggestionEngine.currentWord(rest.trimEnd(' '))
        if (prev.isEmpty()) return
        val next = normalizeForLearning(word)
        viewModelScope.launch { learnedWordsRepository.learnPair(prev, next) }
    }

    private fun setSuggestions(list: List<String>, literal: String, math: MathResult?) {
        transient.update {
            if (it.suggestions == list && it.literal == literal && it.mathResult == math) it
            else it.copy(suggestions = list, literal = literal, mathResult = math)
        }
    }

    private var refreshJob: Job? = null

    /**
     * تحديث الاقتراحات يحتاج قراءة النص من التطبيق (استدعاء بين عمليتين وهو أبطأ شيء بالكتابة)،
     * وكان يُنفَّذ مرتين أو أكثر لكل ضغطة. هلأ نجمع الطلبات المتتالية بتحديث واحد بعد لحظة قصيرة،
     * فتبقى الضغطة نفسها خفيفة وسلسة حتى مع الكتابة السريعة.
     */
    private fun refreshSuggestions() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(30)
            val state = uiState.value
            val t = transient.value
            if (!t.suggestionsAllowed || (!state.suggestionsEnabled && !state.mathResults)) {
                setSuggestions(emptyList(), "", null)
                return@launch
            }
            // قراءة النص من التطبيق (استدعاء بين عمليتين قد يتأخر) وحساب الاقتراحات بخيط خلفي،
            // فلا تتجمد الواجهة حتى لو كان التطبيق ثقيلًا أو القاموس كبيرًا
            val result = withContext(Dispatchers.Default) { computeSuggestions(state, t) }
            if (result.clearSwipe) clearSwipe()
            setSuggestions(result.list, result.literal, result.math)
        }
    }

    private class SuggestionResult(
        val list: List<String>,
        val literal: String,
        val math: MathResult?,
        val clearSwipe: Boolean
    )

    private fun computeSuggestions(state: KeyboardUiState, t: LocalState): SuggestionResult {
        val before = bridge.textBeforeCursor(80)

        // ناتج العملية الحسابية له الأولوية بالشريط
        if (state.mathResults) {
            val math = MathEvaluator.detect(before)
            if (math != null) return SuggestionResult(emptyList(), "", math, false)
        }
        if (!state.suggestionsEnabled) return SuggestionResult(emptyList(), "", null, false)

        // بدائل آخر كلمة كُتبت بالسحب تبقى ظاهرة طالما المؤشر بعدها مباشرة
        var clear = false
        val swipe = t.swipeWord
        if (swipe != null) {
            if (before.endsWith("$swipe ")) return SuggestionResult(t.swipeAlternates, "", null, false)
            clear = true
        }

        val word = SuggestionEngine.currentWord(before)
        if (word.isEmpty()) {
            val trimmed = before.trimEnd()
            val atStart = trimmed.isEmpty() || trimmed.last() in SENTENCE_END
            // إيموجي مقترح للكلمة التي كُتبت للتو (مثل آيفون: "love " ← ❤️)
            val prevWord = if (!atStart && before.endsWith(" ")) SuggestionEngine.currentWord(trimmed) else ""
            val emoji = if (state.suggestEmojis && prevWord.isNotEmpty()) EmojiSuggestions.forWord(prevWord) else null
            val base = when {
                atStart -> SuggestionEngine.starters(state.language)
                // لو ما في توقّع للكلمة السابقة نعرض كلمات بداية شائعة بدل أن يبقى الشريط فارغًا
                state.nextWord -> {
                    val lang = if (prevWord.isNotEmpty()) SuggestionEngine.languageOf(prevWord, state.language) else state.language
                    val predicted =
                        if (prevWord.isNotEmpty()) NextWordModel.predict(prevWord, lang, learnedBigrams.value, 3) else emptyList()
                    predicted.ifEmpty { SuggestionEngine.starters(lang) }
                }
                else -> emptyList()
            }
            return SuggestionResult(if (emoji != null) listOf(emoji) + base.take(2) else base.take(3), "", null, clear)
        }
        val language = SuggestionEngine.languageOf(word, state.language)
        var completions = SuggestionEngine.suggest(word, language, learnedWords.value, limit = 2)
            .filter { !it.equals(word, ignoreCase = true) }
        // لا إكمال للكلمة ولا هي معروفة: نقترح تصحيحها («هل تقصد»)
        if (completions.isEmpty() && state.spellCheck) {
            completions = SuggestionEngine.corrections(word, language, learnedKeys.value, limit = 2)
        }
        val emoji = if (state.suggestEmojis) EmojiSuggestions.forWord(word) else null
        return SuggestionResult(if (emoji != null) completions.take(1) + emoji else completions, word, null, clear)
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '؟')
        /** علامات الترقيم التي تُنهي الكلمة وتشغّل التصحيح التلقائي */
        const val AUTOCORRECT_TRIGGERS = ".,!?;:)\"؟،"
        /** رموز العمليات الحسابية: كتابتها لا تشغّل التصحيح ولا تعلّم الكلمة السابقة */
        const val NON_WORD_END_SYMBOLS = "+×÷=*%^<>~|#"
        const val MAX_RECENT_EMOJIS = 32
        const val FRESH_CLIP_MS = 90_000L
        const val DOUBLE_TAP_MS = 350L
    }
}

class KeyboardViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val clipboardRepository: ClipboardRepository,
    private val learnedWordsRepository: LearnedWordsRepository,
    private val shortcutsRepository: ShortcutsRepository,
    private val bridge: InputBridge
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KeyboardViewModel(settingsRepository, clipboardRepository, learnedWordsRepository, shortcutsRepository, bridge) as T
}
