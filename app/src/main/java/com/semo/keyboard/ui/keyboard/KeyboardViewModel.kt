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
import com.semo.keyboard.domain.logic.NgramModel
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.domain.logic.EmojiSuggestions
import com.semo.keyboard.domain.logic.MathEvaluator
import com.semo.keyboard.domain.logic.SuggestionEngine
import com.semo.keyboard.domain.logic.SwipeDecoder
import com.semo.keyboard.domain.logic.WordLists
import com.semo.keyboard.domain.model.clipImageMime
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.model.clipLongPreview
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isClipLong
import com.semo.keyboard.domain.model.NumKeyTarget
import com.semo.keyboard.domain.model.ClipEdit
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
import com.semo.keyboard.domain.model.TranslateUi
import com.semo.keyboard.domain.model.VoiceOptions
import com.semo.keyboard.domain.model.VoiceStatus
import com.semo.keyboard.domain.model.VoiceUi
import com.semo.keyboard.ime.VoiceLanguages
import com.semo.keyboard.domain.logic.TranslateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    private val hostBridge: InputBridge
) : ViewModel() {

    /** حالة الحاسبة العلمية (تبقى عند التنقل بين الصفحات) */
    val calc = CalcSession()

    /**
     * الجسر الفعلي للكتابة: أثناء تعديل عنصر حافظة تذهب الكتابة والحذف لنص التعديل بدل حقل التطبيق،
     * وبغير ذلك تمرّ مباشرة للجسر الحقيقي.
     */
    private val bridge: InputBridge = object : InputBridge by hostBridge {
        override fun commitText(text: String) {
            if (editing()) editSet(editText() + text) else hostBridge.commitText(text)
        }

        override fun deleteBackward() {
            if (editing()) {
                val t = editText()
                editSet(if (t.length >= 2 && t.last().isLowSurrogate() && t[t.length - 2].isHighSurrogate()) t.dropLast(2) else t.dropLast(1))
            } else hostBridge.deleteBackward()
        }

        override fun deleteSurrounding(count: Int) {
            if (editing()) editSet(editText().dropLast(count)) else hostBridge.deleteSurrounding(count)
        }

        override fun performEnter() {
            if (editing()) editSet(editText() + "\n") else hostBridge.performEnter()
        }

        override fun replaceBeforeCursor(deleteCount: Int, text: String) {
            if (editing()) editSet(editText().dropLast(deleteCount) + text) else hostBridge.replaceBeforeCursor(deleteCount, text)
        }

        override fun commitImage(fileName: String, mime: String): Boolean =
            if (editing()) false else hostBridge.commitImage(fileName, mime)

        override fun textBeforeCursor(length: Int): String =
            if (editing()) editText().takeLast(length) else hostBridge.textBeforeCursor(length)

        override fun textBeforeCursorWithin(length: Int, timeoutMs: Long, skipIfSlow: Boolean): String? =
            if (editing()) editText().takeLast(length) else hostBridge.textBeforeCursorWithin(length, timeoutMs, skipIfSlow)

        override fun moveCursor(delta: Int) {
            if (!editing()) hostBridge.moveCursor(delta)
        }

        override fun moveCursorVertical(lines: Int) {
            if (!editing()) hostBridge.moveCursorVertical(lines)
        }

        override fun performEdit(action: EditAction) {
            if (!editing()) hostBridge.performEdit(action)
        }
    }

    @Volatile private var lastBefore = ""

    private fun readBefore(length: Int): String =
        bridge.textBeforeCursorWithin(length, EDIT_READ_MS, false) ?: lastBefore.takeLast(length)

    private fun editing(): Boolean = transient.value.clipEdit != null
    private fun editText(): String = transient.value.clipEdit?.text ?: ""
    private fun editSet(text: String) {
        transient.update { st -> st.clipEdit?.let { st.copy(clipEdit = it.copy(text = text.takeLast(CLIP_EDIT_MAX))) } ?: st }
    }

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
        val removedWord: String? = null,
        val mathResult: MathResult? = null,
        /** آخر كلمة كُتبت بالسحب وبدائلها، لاستبدالها باختيار بديل من الشريط */
        val swipeWord: String? = null,
        val swipeAlternates: List<String> = emptyList(),
        /** نص نُسخ للتو (شريحة اللصق السريع بشريط الاقتراحات) */
        val freshClip: String? = null,
        val clipEdit: ClipEdit? = null,
        val translate: TranslateUi? = null,
        val voice: VoiceUi? = null,
        val navSetting: Boolean = false,
        val navInset: Boolean = false
    )

    /** تصحيح تلقائي حصل للتو: الرجوع (Backspace) مباشرة بعده يعيد الكلمة الأصلية */
    private data class Correction(val original: String, val fixed: String)

    private var undo: Correction? = null
    private var lastShiftTap = 0L

    private val transient = MutableStateFlow(LocalState())

    private val learnedWords: StateFlow<List<String>> =
        learnedWordsRepository.words.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** الكلمات المتعلَّمة بصيغة مفتاح البحث (للتدقيق الإملائي: الكلمة المتعلَّمة تُعدّ صحيحة) */
    private val learnedKeys: StateFlow<Set<String>> =
        learnedWordsRepository.words
            .map { list ->
                val keys: Set<String> = list.mapTo(HashSet<String>()) { BigDictionary.key(it) }
                keys
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** أزواج الكلمات المتعلَّمة لتوقّع الكلمة التالية */
    private val learnedBigrams: StateFlow<Map<String, List<String>>> =
        learnedWordsRepository.bigrams.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** الكلمات التي رفض المستخدم تصحيحها التلقائي سابقًا (رجع عنها فورًا بالحذف)؛ لا تُصحَّح ثانيةً */
    private val ignoredCorrections: StateFlow<Set<String>> =
        learnedWordsRepository.ignoredCorrections.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val blockedKeys: StateFlow<Set<String>> =
        learnedWordsRepository.blocked
            .map { list ->
                val keys: Set<String> = list.mapTo(HashSet<String>()) { BigDictionary.key(it) }
                keys
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    @Volatile
    private var optimisticBlocked: Set<String> = emptySet()

    private var removedSnapshot: LearnedWordsRepository.BlockSnapshot? = null
    private var removedJob: Job? = null

    private fun allowed(text: String): Boolean {
        val blocked = blockedKeys.value
        val optimistic = optimisticBlocked
        if (blocked.isEmpty() && optimistic.isEmpty()) return true
        val k = BigDictionary.key(text.trim())
        if (k in blocked || k in optimistic) return false
        return k.split(' ').none { it.isNotEmpty() && (it in blocked || it in optimistic) }
    }

    fun removeSuggestion(raw: String) {
        val word = raw.trim().trim('\u201C', '\u201D', '"').trim()
        if (word.isEmpty() || word.none { it.isLetter() }) return
        val state = uiState.value
        if (state.hapticEnabled) bridge.keyFeedback(false, true, KeyFeedback.LONG_PRESS)
        val key = BigDictionary.key(word)
        optimisticBlocked = optimisticBlocked + key
        pendingWords.removeAll { BigDictionary.key(it) == key }
        refreshSuggestions()
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = learnedWordsRepository.blockWord(word)
            if (snapshot == null) {
                optimisticBlocked = optimisticBlocked - key
                return@launch
            }
            removedSnapshot = snapshot
            transient.update { it.copy(removedWord = word) }
            removedJob?.cancel()
            removedJob = viewModelScope.launch {
                delay(REMOVED_NOTICE_MS)
                dismissRemoved()
            }
        }
    }

    fun undoRemoveSuggestion() {
        val snapshot = removedSnapshot ?: return
        removedJob?.cancel()
        removedSnapshot = null
        val key = BigDictionary.key(snapshot.word)
        transient.update { it.copy(removedWord = null) }
        viewModelScope.launch(Dispatchers.Default) {
            learnedWordsRepository.restore(snapshot)
            optimisticBlocked = optimisticBlocked - key
            refreshSuggestions()
        }
    }

    private fun dismissRemoved() {
        removedJob?.cancel()
        removedSnapshot = null
        if (transient.value.removedWord != null) transient.update { it.copy(removedWord = null) }
    }

    private val shortcuts: StateFlow<List<TextShortcut>> =
        shortcutsRepository.shortcuts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<KeyboardUiState> =
        combine(
            transient,
            settingsRepository.settings.distinctUntilChanged(),
            clipboardRepository.items
        ) { t: LocalState, s: SemoSettings, clips: List<ClipItem> ->
            KeyboardUiState(
                page = t.page,
                shiftState = if (s.language.cased) t.shift else ShiftState.OFF,
                language = s.language,
                themeMode = s.themeMode,
                keyboardTheme = s.keyboardTheme,
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
                removedWord = t.removedWord,
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
                enabledLanguages = s.enabledLanguages,
                keyboardScale = s.keyboardScale,
                animationSpeed = s.animationSpeed,
                freshClip = t.freshClip,
                clipEdit = t.clipEdit,
                translate = t.translate,
                voice = t.voice,
                voiceDialect = s.voiceDialect,
                voiceOfflineFirst = s.voiceOfflineFirst,
                navButtons = t.navSetting || t.navInset,
                translateTarget = s.translateTarget,
                translateRecents = s.translateRecents
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
                clipEdit = null,
                translate = null,
                voice = null,
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
        if (transient.value.translate != null) refreshTranslate(false)
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
        if (transient.value.removedWord != null) dismissRemoved()
        if (feedback) {
            bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, feedbackKind(action), state.soundVolume)
        }
        if (transient.value.alternates.isNotEmpty()) dismissAlternates()
        if (transient.value.voice != null) closeVoice()

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
                    val before = bridge.textBeforeCursorWithin(40, HOT_READ_MS, true)
                    if (before != null && !(ch in AUTOCORRECT_TRIGGERS && applyAutoCorrect(before) != null)) learnWord(SuggestionEngine.currentWord(before))
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
                val before = bridge.textBeforeCursorWithin(40, HOT_READ_MS, true)
                if (before == null) {
                    handleSpace(state.language, state.doubleSpacePeriod, "")
                } else {
                    val corrected = applyAutoCorrect(before)
                    val finalWord = corrected?.fixed ?: SuggestionEngine.currentWord(before)
                    learnWord(finalWord)
                    learnPair(before, corrected?.original ?: finalWord, finalWord)
                    val tail = if (corrected == null) before else before.dropLast(corrected.original.length) + corrected.fixed
                    handleSpace(state.language, state.doubleSpacePeriod, tail.takeLast(2))
                    undo = corrected
                }
                refreshSuggestions()
            }
            KeyAction.Enter -> {
                val before = bridge.textBeforeCursorWithin(40, HOT_READ_MS, true)
                if (before != null) {
                    val corrected = applyAutoCorrect(before)
                    val finalWord = corrected?.fixed ?: SuggestionEngine.currentWord(before)
                    learnWord(finalWord)
                    learnPair(before, corrected?.original ?: finalWord, finalWord)
                }
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
                // دورة اللغات المفعّلة بالإعدادات (الافتراضي إنكليزي ↔ عربي)
                val cycle = state.enabledLanguages.ifEmpty { listOf(KeyboardLanguage.ENGLISH, KeyboardLanguage.ARABIC) }
                val next = cycle[(cycle.indexOf(state.language) + 1) % cycle.size]
                setPage(KeyboardPage.LETTERS)
                viewModelScope.launch {
                    // قاموس اللغة ونموذج جملها يُحمَّلان بالخلفية لو لم يكونا محمَّلين
                    withContext(Dispatchers.IO) { DictionaryManager.ensureLoaded(next) }
                    settingsRepository.setLanguage(next)
                }
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
            readBefore(pendingUndo.fixed.length + 1) == pendingUndo.fixed + " "
        ) {
            bridge.deleteSurrounding(pendingUndo.fixed.length + 1)
            bridge.commitText(pendingUndo.original)
            // المستخدم رجع عن التصحيح فورًا: تعلّم ألّا تُصحَّح هذه الكلمة ثانيةً
            viewModelScope.launch { learnedWordsRepository.ignoreCorrection(pendingUndo.original) }
            return
        }
        if (swipeWord != null && readBefore(swipeWord.length + 1) == "$swipeWord ") {
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
        if (fixed == null && state.autoCorrect && word.lowercase() !in ignoredCorrections.value) {
            fixed = AutoCorrect.fix(word)?.takeIf { allowed(it) }
            if (fixed == null) {
                val language = SuggestionEngine.languageOf(word, state.language)
                fixed = SuggestionEngine.smartFix(word, language, learnedKeys.value)?.takeIf { allowed(it) }
            }
        }
        val result = fixed ?: return null
        if (result == word) return null
        bridge.replaceBeforeCursor(word.length, result)
        return Correction(word, result)
    }

    // ---------- الكتابة بالسحب ----------

    /** يُستدعى عند رفع الإصبع بعد سحب على الحروف: [path] الحروف التي مرّ عليها الإصبع بالترتيب */
    fun onSwipeWord(path: List<String>) {
        val state = uiState.value
        val lang = state.language
        val dictionary = WordLists.forLanguage(lang)
        val learned = learnedWords.value.filter { SuggestionEngine.languageOf(it, lang) == lang }
        // فك المسار على خيط خلفي: مع القاموس الكبير قد يأخذ عشرات الميلي ثانية
        viewModelScope.launch {
            val candidates = withContext(Dispatchers.Default) {
                SwipeDecoder.decode(path, dictionary, learned, limit = 3 + SUGGEST_EXTRA, language = lang)
                    .filter { allowed(it) }
                    .take(3)
            }
            if (candidates.isNotEmpty()) commitSwipe(candidates)
        }
    }

    private fun commitSwipe(candidates: List<String>) {
        val state = uiState.value
        val lang = state.language
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
        undo = null
        val shift = if (lang.cased) state.shiftState else ShiftState.OFF
        fun styled(w: String): String = when (shift) {
            ShiftState.OFF -> w
            ShiftState.ON -> w.replaceFirstChar { it.uppercase() }
            ShiftState.LOCKED -> w.uppercase()
        }
        val styledList = candidates.map { styled(it) }
        val word = styledList.first()

        // مسافة قبل الكلمة لو التي قبلها حرف أو رقم
        val last = readBefore(1).lastOrNull()
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
        if (swipeWord != null && readBefore(swipeWord.length + 1) == "$swipeWord ") {
            clearSwipe()
            bridge.replaceBeforeCursor(swipeWord.length + 1, "$suggestion ")
        } else {
            val before = readBefore(40)
            val word = SuggestionEngine.currentWord(before)
            learnPair(before, word, suggestion.substringBefore(' '))
            bridge.replaceBeforeCursor(word.length, "$suggestion ")
        }
        // تتمة من عدة كلمات: نتعلّم كل كلمة وكل زوج متتالٍ فيها (لا نحفظ العبارة كلها ككلمة واحدة)
        val parts = suggestion.split(' ').filter { it.isNotEmpty() }
        parts.forEach { learnWord(it) }
        if (parts.size > 1 && canLearn()) {
            for (i in 1 until parts.size) {
                if (parts[i].length >= 2) pendingPairs.add(BigDictionary.key(parts[i - 1]) to normalizeForLearning(parts[i]))
            }
            scheduleLearnFlush()
        }
        consumeOneShotShift()
        refreshSuggestions()
    }

    /** اختيار ناتج العملية الحسابية: يكتب = والناتج (أو الناتج فقط لو المستخدم كتب = بنفسه) */
    fun onMathChosen() {
        val result = transient.value.mathResult ?: return
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, volume = state.soundVolume)
        undo = null
        val before = readBefore(3).trimEnd(' ')
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

    // ---------- شريط الأدوات والصفحات ----------

    // ---------- translation ----------

    val voiceLevel = MutableStateFlow(0f)

    fun setNavSetting(v: Boolean) {
        if (transient.value.navSetting != v) transient.update { it.copy(navSetting = v) }
    }

    fun setNavInset(v: Boolean) {
        if (transient.value.navInset != v) transient.update { it.copy(navInset = v) }
    }

    private fun voiceTag(): String {
        val state = uiState.value
        return VoiceLanguages.tagFor(state.language, dialect = state.voiceDialect)
    }

    private fun voiceOptions(): VoiceOptions {
        val state = uiState.value
        val lang = state.language
        val keys = learnedKeys.value
        val bias = learnedWords.value.asReversed()
            .asSequence()
            .filter { it.length > 1 && SuggestionEngine.languageOf(it, lang) == lang }
            .take(VOICE_BIAS_MAX)
            .toList()
        val rank: (String) -> Double = { text ->
            val words = text.split(' ', '\n').filter { it.isNotBlank() }
            if (words.isEmpty()) 0.0
            else words.count { w ->
                w.any { c -> c.isDigit() } || SuggestionEngine.isKnown(w.trim(), lang, keys)
            }.toDouble() / words.size
        }
        return VoiceOptions(
            state.voiceOfflineFirst,
            bias,
            rank,
            if (state.soundEnabled) state.soundVolume.coerceAtLeast(0.4f) else 0f
        )
    }

    fun openVoiceSettings() {
        closeVoice()
        bridge.openVoiceSettings()
    }

    fun openVoice() {
        transient.update {
            it.copy(
                voice = VoiceUi(VoiceStatus.STARTING),
                translate = null,
                clipEdit = null,
                toolbarOpen = false,
                page = KeyboardPage.LETTERS,
                alternates = emptyList()
            )
        }
        translateJob?.cancel()
        bridge.voiceStart(voiceTag(), voiceOptions())
    }

    fun closeVoice() {
        if (transient.value.voice == null) return
        bridge.voiceCancel()
        voiceLevel.value = 0f
        transient.update { it.copy(voice = null) }
    }

    fun toggleVoice() {
        val v = transient.value.voice ?: return
        when (v.status) {
            VoiceStatus.LISTENING, VoiceStatus.STARTING -> bridge.voiceFinish()
            else -> {
                transient.update { st -> st.voice?.let { st.copy(voice = it.copy(status = VoiceStatus.STARTING, text = "")) } ?: st }
                bridge.voiceStart(voiceTag(), voiceOptions())
            }
        }
    }

    fun onVoiceStatus(status: VoiceStatus) {
        if (status != VoiceStatus.LISTENING) voiceLevel.value = 0f
        transient.update { st ->
            st.voice?.let { st.copy(voice = it.copy(status = status, text = if (status == VoiceStatus.LISTENING) it.text else "")) } ?: st
        }
    }

    fun onVoicePartial(text: String) {
        transient.update { st -> st.voice?.let { if (it.text == text) st else st.copy(voice = it.copy(text = text)) } ?: st }
    }

    fun onVoiceLevel(level: Float) {
        voiceLevel.value = level
    }

    private var translateJob: Job? = null
    private var translateKey = ""

    fun openTranslate() {
        closeVoice()
        transient.update { it.copy(translate = TranslateUi(), toolbarOpen = false, page = KeyboardPage.LETTERS, alternates = emptyList()) }
        translateKey = ""
        refreshTranslate(true)
    }

    fun closeTranslate() {
        translateJob?.cancel()
        translateKey = ""
        transient.update {
            it.copy(translate = null, page = if (it.page == KeyboardPage.TRANSLATE) KeyboardPage.LETTERS else it.page)
        }
    }

    fun toggleTranslatePicker() {
        transient.update {
            it.copy(page = if (it.page == KeyboardPage.TRANSLATE) KeyboardPage.LETTERS else KeyboardPage.TRANSLATE, alternates = emptyList())
        }
    }

    fun setTranslateTarget(code: String) {
        viewModelScope.launch {
            settingsRepository.setTranslateTarget(code)
            transient.update { if (it.page == KeyboardPage.TRANSLATE) it.copy(page = KeyboardPage.LETTERS) else it }
            translateKey = ""
            refreshTranslate(true)
        }
    }

    fun retryTranslate() {
        translateKey = ""
        refreshTranslate(true)
    }

    fun applyTranslation() {
        val ui = transient.value.translate ?: return
        if (ui.result.isEmpty() || ui.loading) return
        val state = uiState.value
        hostBridge.keyFeedback(state.soundEnabled, state.hapticEnabled, KeyFeedback.MODIFIER, state.soundVolume)
        hostBridge.replaceFieldText(ui.result)
        closeTranslate()
        refreshSuggestions()
    }

    private fun refreshTranslate(immediate: Boolean) {
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            if (!immediate) delay(350)
            if (transient.value.translate == null) return@launch
            val target = uiState.value.translateTarget
            if (!transient.value.suggestionsAllowed) {
                translateKey = ""
                transient.update { st -> st.translate?.let { st.copy(translate = TranslateUi()) } ?: st }
                return@launch
            }
            val text = withContext(suggestDispatcher) { hostBridge.fieldText() }.trim()
            val key = "$target|$text"
            if (key == translateKey && !transient.value.translate!!.error) return@launch
            if (text.isEmpty()) {
                translateKey = key
                transient.update { st -> st.translate?.let { st.copy(translate = TranslateUi()) } ?: st }
                return@launch
            }
            val cached = TranslateEngine.cached(text, target)
            if (cached != null) {
                translateKey = key
                transient.update { st -> st.translate?.let { st.copy(translate = TranslateUi(text, cached.text, cached.detected)) } ?: st }
                return@launch
            }
            transient.update { st -> st.translate?.let { st.copy(translate = it.copy(source = text, loading = true, error = false)) } ?: st }
            val result = withContext(Dispatchers.IO) { TranslateEngine.translate(text, target) }
            if (transient.value.translate == null) return@launch
            if (result == null) {
                transient.update { st -> st.translate?.let { st.copy(translate = it.copy(loading = false, error = true)) } ?: st }
            } else {
                translateKey = key
                transient.update { st -> st.copy(translate = TranslateUi(text, result.text, result.detected)) }
            }
        }
    }

    fun toggleToolbar() {
        transient.update { it.copy(toolbarOpen = !it.toolbarOpen) }
    }

    fun openPage(page: KeyboardPage) {
        if (page != KeyboardPage.LETTERS) closeVoice()
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
                val full = clipboardRepository.fullText(text)?.takeIf { it.isNotEmpty() } ?: text.clipLongPreview()
                if (full.isNotEmpty()) {
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

    /** بدء تعديل عنصر: يُحمَّل نصه الكامل ويُكتب تعديله بلوحة المفاتيح نفسها ثم يُحفظ بالضغط على ✓ */
    fun onClipEditStart(stored: String) {
        if (stored.isClipImage()) return
        viewModelScope.launch {
            val full = clipboardRepository.fullText(stored) ?: return@launch
            transient.update {
                it.copy(
                    clipEdit = ClipEdit(stored, full),
                    page = KeyboardPage.LETTERS,
                    toolbarOpen = false,
                    alternates = emptyList(),
                    suggestions = emptyList(),
                    literal = "",
                    mathResult = null,
                    freshClip = null
                )
            }
            refreshSuggestions()
        }
    }

    fun onClipEditSave() {
        val edit = transient.value.clipEdit ?: return
        transient.update { it.copy(clipEdit = null, page = KeyboardPage.CLIPBOARD, suggestions = emptyList(), literal = "", mathResult = null) }
        viewModelScope.launch { clipboardRepository.replace(edit.key, edit.text) }
    }

    fun onClipEditCancel() {
        transient.update { it.copy(clipEdit = null, page = KeyboardPage.CLIPBOARD, suggestions = emptyList(), literal = "", mathResult = null) }
    }

    /** صوت/اهتزاز ضغطة مفتاح بلوحة الحاسبة */
    fun onCalcTick(delete: Boolean = false) {
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, if (delete) KeyFeedback.DELETE else KeyFeedback.STANDARD, state.soundVolume)
    }

    /** إدراج ناتج الحاسبة (أو المعادلة كاملة) بحقل الكتابة */
    fun onCalcInsert(text: String) {
        if (text.isEmpty()) return
        val state = uiState.value
        bridge.keyFeedback(state.soundEnabled, state.hapticEnabled, KeyFeedback.MODIFIER, state.soundVolume)
        bridge.commitText(text)
        refreshSuggestions()
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
        if (!language.cased || !uiState.value.autoCapitalize) return
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

    private val pendingWords = ArrayList<String>()
    private val pendingPairs = ArrayList<Pair<String, String>>()
    private var learnFlushJob: Job? = null

    private fun scheduleLearnFlush() {
        learnFlushJob?.cancel()
        learnFlushJob = viewModelScope.launch {
            delay(6_000)
            flushLearning()
        }
    }

    /** يكتب ما تعلّمته اللوحة دفعة واحدة (يُستدعى بعد هدوء الكتابة وعند إخفاء اللوحة) */
    fun flushLearning() {
        learnFlushJob?.cancel()
        if (pendingWords.isEmpty() && pendingPairs.isEmpty()) return
        val words = pendingWords.toList()
        val pairs = pendingPairs.toList()
        pendingWords.clear()
        pendingPairs.clear()
        viewModelScope.launch(Dispatchers.Default) { learnedWordsRepository.learnBatch(words, pairs) }
    }

    private fun learnWord(word: String) {
        if (!canLearn() || word.length < 3) return
        val normalized = normalizeForLearning(word)
        pendingWords.add(normalized)
        scheduleLearnFlush()
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
        val before2 = rest.trimEnd(' ').dropLast(prev.length)
        val prev2 = if (before2.endsWith(' ')) SuggestionEngine.currentWord(before2.trimEnd(' ')) else ""
        pendingPairs.add(prev to next)
        if (prev2.isNotEmpty()) pendingPairs.add("${BigDictionary.key(prev2)} ${BigDictionary.key(prev)}" to next)
        scheduleLearnFlush()
    }

    private fun setSuggestions(list: List<String>, literal: String, math: MathResult?) {
        transient.update {
            if (it.suggestions == list && it.literal == literal && it.mathResult == math) it
            else it.copy(suggestions = list, literal = literal, mathResult = math)
        }
    }

    private var refreshJob: Job? = null

    private val suggestExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "semo-suggest").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val suggestDispatcher = suggestExecutor.asCoroutineDispatcher()

    override fun onCleared() {
        suggestExecutor.shutdown()
        super.onCleared()
    }

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
            val result = withContext(suggestDispatcher) { computeSuggestions(state, t) } ?: return@launch
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

    private fun computeSuggestions(state: KeyboardUiState, t: LocalState): SuggestionResult? {
        val before = bridge.textBeforeCursorWithin(80, SUGGEST_READ_MS, false) ?: return null
        lastBefore = before

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
            if (before.endsWith("$swipe ")) return SuggestionResult(t.swipeAlternates.filter { allowed(it) }, "", null, false)
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
                atStart -> {
                    val lang = state.language
                    (NgramModel.starters(lang, 3 + SUGGEST_EXTRA) + SuggestionEngine.starters(lang))
                        .distinctBy { it.lowercase() }.filter { allowed(it) }.take(3)
                }
                state.nextWord -> {
                    val lang = if (prevWord.isNotEmpty()) SuggestionEngine.languageOf(prevWord, state.language) else state.language
                    if (prevWord.isNotEmpty()) {
                        val ctx = lastWords(trimmed, 3)
                        val prev2 = if (ctx.size >= 2) ctx[ctx.size - 2] else ""
                        val words = NextWordModel.predict(prevWord, lang, learnedBigrams.value, 3 + SUGGEST_EXTRA, prev2)
                            .filter { allowed(it) }.take(3)
                        val withPhrase = if (words.isNotEmpty()) {
                            val phrase = fitPhrase(NextWordModel.phrase(lang, learnedBigrams.value, ctx, words[0], 4))
                                ?.takeIf { allowed(it) }
                            if (phrase != null) listOfNotNull(words[0], phrase, words.getOrNull(1)) else words
                        } else words
                        withPhrase.ifEmpty { SuggestionEngine.starters(lang).filter { allowed(it) } }
                    } else SuggestionEngine.starters(lang).filter { allowed(it) }
                }
                else -> emptyList()
            }
            return SuggestionResult(if (emoji != null) listOf(emoji) + base.take(2) else base.take(3), "", null, clear)
        }
        val language = SuggestionEngine.languageOf(word, state.language)
        val ctxWords = lastWords(before.dropLast(word.length), 2)
        val ctxPrev = ctxWords.lastOrNull() ?: ""
        val ctxPrev2 = if (ctxWords.size >= 2) ctxWords[0] else ""
        var completions = SuggestionEngine.suggest(
            word, language, learnedWords.value, limit = 3 + SUGGEST_EXTRA,
            context = ctxPrev, bigrams = learnedBigrams.value, context2 = ctxPrev2
        ).filter { !it.equals(word, ignoreCase = true) && allowed(it) }.take(3)
        // الكلمة غير معروفة وما كفت الإكمالات: نكمّل بتصحيحات «هل تقصد»
        if (completions.size < 2 && state.spellCheck) {
            completions = (completions + SuggestionEngine.corrections(word, language, learnedKeys.value, limit = 2 + SUGGEST_EXTRA)
                .filter { allowed(it) })
                .distinctBy { it.lowercase() }
        }
        // تتمة الجملة: من أفضل إكمال نكمل بكلمات مرجّحة (الخانة الثانية) بدل إكمال ثانٍ
        var list = completions.take(2)
        if (completions.isNotEmpty() && state.nextWord) {
            val phrase = fitPhrase(NextWordModel.phrase(language, learnedBigrams.value, ctxWords, completions[0], 4))
                ?.takeIf { allowed(it) }
            if (phrase != null) list = listOf(completions[0], phrase)
        }
        val emoji = if (state.suggestEmojis) EmojiSuggestions.forWord(word) else null
        return SuggestionResult(if (emoji != null) list.take(1) + emoji else list, word, null, clear)
    }

    /** آخر [n] كلمات من [text] (بلا تجاوز علامات الترقيم) */
    private fun lastWords(text: String, n: Int): List<String> {
        val out = ArrayList<String>()
        var rest = text.trimEnd(' ')
        while (out.size < n && rest.isNotEmpty()) {
            val w = SuggestionEngine.currentWord(rest)
            if (w.isEmpty()) break
            out.add(0, w)
            rest = rest.dropLast(w.length).trimEnd(' ')
        }
        return out
    }

    /** يقصّ التتمة لتتسع بخانة الشريط (حتى 24 حرفًا)؛ null لو أقل من كلمتين */
    private fun fitPhrase(words: List<String>): String? {
        val out = StringBuilder()
        var count = 0
        for (w in words) {
            val add = if (out.isEmpty()) w.length else w.length + 1
            if (out.length + add > 24) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(w)
            count++
        }
        return if (count >= 2) out.toString() else null
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '؟')
        /** علامات الترقيم التي تُنهي الكلمة وتشغّل التصحيح التلقائي */
        const val AUTOCORRECT_TRIGGERS = ".,!?;:)\"؟،"
        /** رموز العمليات الحسابية: كتابتها لا تشغّل التصحيح ولا تعلّم الكلمة السابقة */
        const val NON_WORD_END_SYMBOLS = "+×÷=*%^<>~|#"
        const val MAX_RECENT_EMOJIS = 32
        const val HOT_READ_MS = 80L
        const val EDIT_READ_MS = 120L
        const val SUGGEST_READ_MS = 400L
        const val FRESH_CLIP_MS = 180_000L
        const val CLIP_EDIT_MAX = ClipboardRepository.MAX_TEXT_LENGTH
        const val VOICE_BIAS_MAX = 120
        const val DOUBLE_TAP_MS = 350L
        const val SUGGEST_EXTRA = 3
        const val REMOVED_NOTICE_MS = 4000L
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
