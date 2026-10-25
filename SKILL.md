---
name: semo-keyboard
description: Working notes for the Semo Keyboard Android project (Kotlin, Compose, IME). Read before changing keyboard UI, toolbar, settings or suggestions.
---

# Project
- Package com.semo.keyboard, IME service in ime/SemoKeyboardService.kt, UI in ui/keyboard (KeyboardScreen, Panels, CalcPanel, TranslatePanel), state in ui/keyboard/KeyboardViewModel.kt, models in domain/model/KeyModels.kt, prefs in data/SettingsRepository.kt.
- Full history of earlier tasks is in TASKS_LOG.md.
- Rules from the owner: never create README.md; code files contain code only, no Arabic text or comments; UI text goes through string resources (values and values-ar).

# Task log
## Translation feature (v3.3.0, versionCode 24)
- Translate via Google endpoint (translate.googleapis.com gtx, fallback clients5.google.com), auto-detect source, any target from a 100+ language list: domain/logic/TranslateEngine.kt (TranslateEngine, TranslateLanguages), 64-entry LRU cache.
- New ToolbarItem.TRANSLATE and KeyboardPage.TRANSLATE (language picker). Toolbar item is appended once for existing users via TOOLBAR_TRANSLATE_SEEN.
- Translate bar replaces the top strip while active: target-language chip (opens picker), translated text pill (tap to replace field text), close button. Defined in ui/keyboard/TranslatePanel.kt, shown by the strip Crossfade in KeyboardScreen.
- ViewModel: openTranslate, closeTranslate, toggleTranslatePicker, setTranslateTarget, retryTranslate, applyTranslation, refreshTranslate (350ms debounce, triggered from onCursorMoved). Disabled in password, email and URL fields (suggestionsAllowed false) so text is never sent.
- InputBridge gained fieldText() and replaceFieldText(); implemented in the service with batch edit.
- Settings keys: translate_target, translate_recents (max 6), toolbar_translate_seen. Icon: drawable/ic_tool_translate.xml.
- Not yet verified by a Gradle build; check CI output first if something fails.

## Lag fixes (v3.3.1, versionCode 25)
- Picking a suggestion word from the strip froze the UI: explicit field reads on the main thread waited up to 1200ms. Now EXPLICIT_READ_MS is 250ms, the ViewModel uses readBefore() (120ms read, falls back to the last text computed by computeSuggestions via lastBefore).
- Suggestion pick and auto-correct now use InputBridge.replaceBeforeCursor(deleteCount, text): one batch edit instead of separate delete and commit calls.
- Translation field replace runs on ioHandler; fieldText() read uses its own 1500ms budget on a background dispatcher.
- Checked and left as is: clipboard capture, screenshot capture and ClipboardRepository.prepare already run off the main thread.

## iOS motion pass (v3.4.0, versionCode 26)
- Motion tokens in ui/anim/AnimScale.kt: IosEaseInOut, IosEaseOut, IosPush (cubic-bezier 0.32,0.72,0,1), damping constants. scaledTween default easing is now IosEaseInOut.
- Springs retuned with little or no overshoot (damping 0.72 to 1.0): key preview, alternates bubble, toolbar icon pop, press scale (0.9), emoji panel, calc pulse and keys, language switch slide.
- Page transitions in the keyboard use IosPush slides plus IosEaseOut fades; emoji category scroll 450ms IosPush.
- Settings navigation: 480ms IosPush push/pop with 30% parallax on the page underneath.
- ui/anim/IosControls.kt: IosIndication (gray press highlight that fades out, replaces Material ripple app-wide via SemoAppTheme) and IosSwitch (51x31 track, spring thumb with press stretch) used by SwitchRow and toolbar rows.
- Not yet verified by a Gradle build.

## Prediction, Arabic autocorrect and themes (v3.5.0, versionCode 27)
- domain/logic/ArabicText.kt: ModelVersion (bumped when dictionaries or n-gram models load), LruCache, Arabic clitic and suffix stripping (stems), canonicalHamza (restores hamza forms of common dictionary words, length 4+).
- SuggestionEngine: suggest/corrections/smartFix are wrapped by LRU caches keyed by language, text, context, ModelVersion and identity of learned collections. Arabic: completion with attached prefixes (و ف ب ل ك ال وال بال ...), isKnown understands prefixes and pronoun suffixes (fewer false corrections), smartFix fixes the stem under a prefix and restores hamza, ending ه offers the ة variant as a suggestion only.
- NextWordModel.predict is cached the same way.
- AutoCorrect table: about 40 more Arabic entries (hamza forms, common typos, set phrases).
- Themes: KeyboardTheme enum (IOS, MIDNIGHT, OCEAN, FOREST, ROSE, SUNSET, LAVENDER, GRAPHITE, MINT), each with light and dark palette in ui/theme/SemoTheme.kt via semoColors(style, dark, theme). Stored as keyboard_theme. Picker with live preview and cards in ui/settings/ThemePicker.kt, shown first in the Appearance page; the old light/dark selector is labelled as mode.
- Not yet verified by a Gradle build.

## App language, cache cycle, backup, smoother pages (v3.6.0, versionCode 28)
- App language (Arabic, English, System): AppLanguage enum, stored as app_language. ui/AppStrings.kt: AppLang (state, loads assets/strings_en.json), L(arabicText) returns the English text when the app language is English, appString(resId) reads a string resource in the chosen app language. All Arabic literals in settings, onboarding, MainActivity and UpdateManager are wrapped with L(); template strings wrap each Arabic chunk. To add a new UI string: use string resources (values and values-ar) with appString, or wrap an Arabic literal with L() and add the English line to assets/strings_en.json. Layout direction follows the language (Ltr for English).
- Language picker: AppLanguageBlock in ui/settings/AppSettingsExtras.kt (three tiles), first section on the home page.
- Cache: data/CacheCleaner.kt clears in-memory caches (suggestions, corrections, next word, translation) and files in cacheDir/externalCacheDir older than 30 minutes every 6 hours, driven by SemoKeyboardService.startCacheCycle using last_cache_clear. Toggle auto_cache_clear and a clear-now button are in Appearance, section Performance. User data is never touched.
- Backup: data/BackupManager.kt exports and imports the whole DataStore as JSON (settings, learned words and pairs, ignored corrections, shortcuts); excludes clipboard history, onboarding flag and cache timestamp. UI in the About page (BackupBlock) using the system file picker.
- Fixed: resetToDefaults used to wipe learned words, shortcuts and clipboard because they live in the same DataStore; it now keeps them (KEEP_ON_RESET) plus app language.
- Keyboard page transitions use one timing family (fade 240ms, slide 360ms IosPush, exit fade 100ms); emoji font-support list is prewarmed on a background thread at service start to avoid a hitch on first open.
- Not yet verified by a Gradle build.

## Voice typing: dialects, offline packs, better capture (v3.8.0, versionCode 30)
- VoiceDialect enum (KeyModels.kt) with Arabic region tags; settings keys voice_dialect and voice_offline_first. VoiceLanguages.tagFor takes the dialect (AUTO follows device region).
- VoiceOptions (offlineFirst, bias words, rank function) is passed through InputBridge.voiceStart. The view model builds bias strings from learned words and a rank function from SuggestionEngine.isKnown.
- VoiceInput: asks for 8 alternatives, re-ranks them by recognizer confidence plus dictionary match, sends EXTRA_BIASING_STRINGS, can use the on-device recognizer first and falls back to online (and the reverse on network errors).
- ime/OfflineSpeech.kt: checkRecognitionSupport and triggerModelDownload (API 33+, progress listener on API 34+), system settings fallback for older devices.
- Settings page SettingsPage.VOICE (ui/settings/VoiceSettings.kt): dialect list, prefer-offline switch, per-language pack status and download. The voice bar opens it on NETWORK or UNAVAILABLE via InputBridge.openVoiceSettings and SettingsDeepLink.
- Navigation-buttons mode: height and inset math in KeyboardScreen made non-throwing. Root cause of the reported crash not reproduced without a stack trace.
- Not yet verified by a Gradle build.

## Built-in Arabic voice engine, navigation mode detection, bigger Arabic autocorrect (v3.9.0, versionCode 31)
- Problem: Google offline Arabic pack shows "not available on this device" (ar-SY) on some phones, and the system recognizer cannot be forced to install it.
- Fix: ime/VoskModelManager.kt downloads the open-source Vosk Arabic model vosk-model-ar-mgb2-0.4 (Apache 2.0, about 333 MB zip) from https://alphacephei.com/vosk/models/ with resume, unpacks it to filesDir/vosk/ar (zip-slip guarded, .ready marker) and exposes StateFlow<VoskState>. The process-level scope keeps downloading when the settings screen closes.
- ime/VoskVoice.kt records with AudioRecord (16 kHz mono, VOICE_RECOGNITION), feeds org.vosk.Recognizer, posts partial/final text and mic level through VoiceInput.Callback. Model is released 90 s after the last use (VoskModelManager.idle).
- ime/VoiceInput.kt: for Arabic tags uses the built-in engine when the model is installed and (voice_builtin_first is on, or the system recognizer is unavailable, language unsupported, or network/offline failed). New setting voice_builtin_first (SemoSettings, KeyboardUiState, VoiceOptions.builtinFirst).
- Settings page Voice typing has a new section "Built-in Arabic engine" (download, cancel, delete, prefer switch); strings voice_builtin_* in values and values-ar.
- Dependency com.alphacephei:vosk-android:0.3.75 (pulls JNA). ProGuard keeps org.vosk and com.sun.jna.
- Limits: standard Arabic works best, dialects less; big model needs RAM and about 1 GB free storage; no checksum is enforced (size check plus structure check am/final.mdl).
- Navigation mode: util/NavigationMode.kt votes between Samsung navigation_bar_gesture_while_hidden, Settings.Secure navigation_mode and config_navBarInteractionMode; KeyboardScreen also treats tappable inset or an edge-to-edge nav inset of 40dp or more as button mode. In button mode the utility row (globe and hide button) is not drawn, the globe moves into the bottom letter row and the system back/hide arrow is the only hide control. Gesture mode is unchanged.
- Arabic autocorrect: the Arabic table moved out of code into assets/dict/ar_fix.txt (about 1,680 tab-separated pairs: hamza on alef, wrong hamza before forms VII to X, ta marbuta, alef maqsura, hamza on waw and ya, fixed phrases, thanks to the generator kept outside the project). AutoCorrect.attach(context) loads it from the service onCreate. Prefix handling (و ب ل ك, وب ول وك, لل) only for stems of 4 or more letters so valid words such as واحد, واضح, فاخر stay untouched.
- ArabicText.endingFix: dictionary-aware ه to ة (only when the ة form is more frequent than the stem) and ي to ى. SuggestionEngine.smartFix for Arabic is skipped until the dictionary has at least 3,000 words, to avoid false corrections with the tiny built-in list.
- Deliberately not mapped (ambiguous): انه انها انك انني, اعداد, احياء, اشكال, اشعار, امسك, انهار, and words whose ه form is a common pronoun form (عمله, دوره, كسره...).
- Not yet verified by a Gradle build; check CI output first if something fails.

## Clipboard fixes, voice cues, bar and toolbar design (v3.9.2, versionCode 33)
- Screenshots: MediaStore query uses a Bundle with QUERY_ARG_LIMIT (LIMIT inside sortOrder is rejected on Android 11+ and was swallowed by runCatching). ContentObserver on MediaStore.Images in SemoKeyboardService triggers captureScreenshot with retries; lastShotId is set only after a successful save.
- Clipboard capture: lastCapturedClip and lastClipTs are set only after ClipboardRepository.add succeeds; failure counter CLIP_MAX_FAILS; longer read retries. MAX_TEXT_LENGTH is 1,000,000. Long-text cards show a length badge.
- Voice cues: res/raw/voice_start, voice_end, voice_cancel; ime/VoiceCues.kt (VoiceCuePlayer, SystemBeepMuter). VoiceInput.beginWithCue delays recognition 170ms; cancelByUser plays the cancel cue; VoiceOptions.cueVolume comes from the key sound setting. SystemBeepMuter mutes only STREAM_MUSIC in short windows.
- UI: BarWidgets.kt has rememberBarEnter, barEntrance, BarCapsule, BarCloseButton shared by TranslateBar and VoiceBar. KeyboardScreen.StripIconButton takes tint and active; toolTint(item) holds the per-tool colors.
- Not yet verified by a Gradle build.

## Ideas for next steps
- Optional per-app or per-field source language override; swap button; translate selected text only.

## Icon hiding fix, permanent word removal from suggestions (v3.9.4, versionCode 35)
- Hide icon root cause: since Android 10 the launcher shows a synthesized icon (opens App info) for any non-system app that requests permissions and has a launcher activity enabled by default in the manifest, even if that activity is disabled at runtime. The LAUNCHER alias (.ui.MainActivityLauncher) is now android:enabled="false" in the manifest and is switched on at runtime only while the icon should show. AppIconVisibility.isIconVisible is true only for COMPONENT_ENABLED_STATE_ENABLED; setIconVisible skips no-op writes.
- MainActivity keeps MAIN + category INFO so the installer Open button and App info Open button still work without a launcher entry.
- SettingsRepository.syncAppIcon() applies the saved hide_app_icon value; called from MainActivity.onCreate and SemoKeyboardService.onCreate (background). setHideAppIcon applies the state immediately. The old LaunchedEffect in PrivacyPage was removed.
- Existing installs: after updating, an icon that was never hidden reappears once the app or the keyboard has started once. Some launchers need a restart to drop the old synthesized icon.
- Suggestion bar: long press on a suggestion or on the typed word removes it for good. LearnedWordsRepository gets blocked_words (blocked flow, blockWord returns a BlockSnapshot, restore, unblockWord, clearBlocked); blockWord deletes the word and its bigram pairs from learning, learnBatch skips blocked words. blocked_words is kept on reset and included in backup.
- KeyboardViewModel: blockedKeys + optimisticBlocked, allowed() filters starters, next-word, completions, corrections, phrases, swipe alternates and auto-correct targets; limits are raised by SUGGEST_EXTRA so the bar stays full. removeSuggestion, undoRemoveSuggestion, dismissRemoved; the notice auto-dismisses after 4 s or on the next key.
- Strip UI (KeyboardScreen): SuggestionCell with press highlight, tap and long press through detectTapGestures, font shrink for long phrases, accessibility long-click label; RemovedNotice (trash icon, message, Undo pill, countdown line) crossfades in place of the slots. Strings suggest_* and blocked_* in values and values-ar; keyboardString() picks the keyboard language.
- Settings, Dictionary page: new section listing removed words with Restore and Restore all.
- Not yet verified by a Gradle build.

## Memory trim, calmer animations, new icon (v3.9.5, versionCode 36)
- VoskModelManager: idle release 20s, busy flag, trim() called from SemoKeyboardService.onTrimMemory (UI_HIDDEN and above) together with CacheCleaner.clearMemory().
- TranslatePanel: language chip pulse only runs while loading. KeyboardScreen: key background color is read in the draw phase (drawBehind plus drawOutline).
- Icon: keycap with voice waveform and spacebar pill on a violet gradient; drawables ic_launcher_background, ic_launcher_foreground, ic_launcher_monochrome (outline keycap plus waveform) and ic_logo.
- Not yet verified by a Gradle build.

## Paste, clipboard and lifecycle fixes (v3.9.6, versionCode 37)
- Long paste: SemoKeyboardService.commitChunked runs under pasteMutex, always ends the batch edit in finally, and on failure sends only the unsent remainder through the clipboard.
- Clipboard capture: multi-item clips are joined, HTML conversion is size-guarded, U+FFFC is stripped, CRLF and CR become LF, duplicate key is FNV-64. Long items with a missing file paste their preview; editing keeps up to ClipboardRepository.MAX_TEXT_LENGTH.
- ClipboardRepository.pruneOrphans (with retain on both stores) runs from the cache cycle.
- onDestroy cancels clipJob and clears all mainHandler callbacks; MainActivity icon sync uses lifecycleScope.
- Removed unused code: dismissFreshClip, DictionaryManager.isAvailable/hasDownloaded, the voiceBuiltinFirst chain, VoiceOptions.builtinFirst, PasteGreen, two damping constants, ARABIC_LABEL, voice_builtin_pref strings, seven drawables, unused imports.
- Not yet verified by a Gradle build.

## Arabic correction, learning and smart writing (v3.17.0, versionCode 52)
- Spell stack: domain/logic/KeyProximity.kt (weighted edit cost), SpellIndex.kt (deletion index per language, built lazily and warmed in DictionaryManager.load), SpellFix.kt (rank and autoFix with previous-word context). SuggestionEngine.corrections and smartFix delegate to it.
- SentenceRules.kt reads assets/dict/ar_rules.txt (tab separated wrong and right phrases, up to 4 tokens) and also provides dedupe, spacing and finishQuestion. Attached in SemoKeyboardService next to AutoCorrect.attach.
- Manual-edit learning lives in KeyboardViewModel (EditWatch, trackEditStart, finishEditWatch, learnManualEdit). learnFix(weight = 2) makes it trusted at once. Throttled by TRACK_GAP_MS.
- Arabizi.kt turns Latin letters and digits 2 3 5 7 8 9 into Arabic suggestions using BigDictionary.hasPrefix; gated by the smartWriting setting (string resources smart_writing_*).
- DictionaryManager.BASE_URLS lists mirrors; assets/dict/ar_extra.txt is merged after ar_core.
- Not yet verified by a Gradle build.

## Clipboard rewrite: files, big text, faster paste (v3.21.0, versionCode 56)
- ClipboardRepository now sits on SQLite (data/ClipDb.kt, one row per item); public API and stored key formats unchanged; one-time migration from the old clipboard_items DataStore key. No Room on purpose (no new dependency or KSP).
- New item kind: file (domain/model/KeyModels.kt clipFileKey and helpers, ClipKind.FILE, data/ClipFileStore.kt). Any non-image, non-text content URI is copied to filesDir/clip_files. Paste uses commitContent when the field accepts the mime, else copies a FileProvider URI to the clipboard and toasts (clip_file_copied).
- Limits in data/ClipLimits.kt (SharedPreferences clip_limits): largest item (default 100 MB) and total budget (default 500 MB); trim() in the repository drops the oldest unpinned non-text items first, never the newest or pinned ones. UI in the Clipboard and privacy settings page.
- Capture: clipListener reads primaryClip immediately on the main thread and passes it to captureClipboard(early); image mime also sniffed from magic bytes (ClipFileStore.sniffImageMime); text-like files read by extension (ClipFileStore.TEXT_EXTENSIONS). ClipNotice (TOO_LARGE, EXPIRED, STORAGE_FULL) is shown on the suggestion strip and in the clipboard panel via KeyboardViewModel.onClipFailed.
- Long text: ClipTextStore writes gzip (.gz), reads both .gz and legacy .txt. InputBridge gained commitFile, copyFileToClipboard, copyLargeTextToClipboard (file URI above 150k chars) and cancelPaste. commitChunked adapts chunk size to the target app (45 ms goal) and reports progress through KeyboardViewModel.onPasteProgress (strip with cancel for texts of 40k chars or more).
- Settings page also has battery rows (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS and app details) because Samsung kills the IME process in the background.
- Not yet verified by a Gradle build; check CI output first if something fails.

## Learning and suggestion quality (v3.22.0, versionCode 57)
- Why one use did not stick: a learned word is trusted (exempt from autocorrect) only at effective strength 0.7, which took about 3 uses. Unknown, non-typo-like words are now learned with weight 3 (trusted at once); known words keep weight 1.
- KeyboardViewModel.learnWord(word, before) classifies: name (LearnFilter.looksLikeName) or far-from-dictionary word -> pendingStrong (flushed at once for names), typo-like word (isTypoLike, edit distance 1 from a correction) -> pendingSuspects (promoted on second use via learn_candidates), known word -> pendingWords. LearnFilter.acceptable rejects digits, mixed scripts, repeats. Dictionary not loaded -> old behavior.
- Autocorrect undo (handleBackspace): retractPending(fixed), learnStrong(original), ignoreCorrection, forgetFix. First backspace into the last finished word retracts it from pending lists; replacing it calls weakenWord(original).
- LearnedEntry has uses (optional fifth field, estimated from strength for old data); ForgettingCurve.halfLifeHours doubles per use up to 5 doublings.
- Rank: PersonalRank (domain/logic/PersonalRank.kt) implemented by KeyboardViewModel.RankSnapshot from wordScores, rank_choices (prefix to word, 30 day half-life) and rank_profiles (per InputProfile). SuggestionEngine.suggest(rank = ...) adds boost and includes rank.version in the cache key. Service passes InputProfile.classify(packageName, search) to onStartInput.
- Learned notice: transient learnedNotice list shown on the strip with Undo (undoLearned), cleared on typing or after 4 s. Strings learned_one, learned_many, learned_undo.
- Script filter on next-word and completions: LearnFilter.keepSameScript.
- Not yet verified by a Gradle build; check CI output first if something fails.

## Plain line toolbar, bigger circles, Samsung emoji order (v3.23.0, versionCode 58)
- Top strip tools (KeyboardScreen StripIconButton): no colored tint, no gradient or border; plain outline icons (23dp) in the text color, only the active tool gets a soft chip background. toolTint removed.
- Bottom circles (globe and hide ring): 36dp to 44dp, icon 20dp to 24dp, container 42dp to 48dp with the same center (24dp constants in KeyboardScreen util row and globeMax).
- Emoji order: domain/logic/EmojiOrder.kt (generated from the Unicode CLDR order) regroups EmojiData plus EmojiExtra into 8 sections like Samsung: Smileys & People, Nature, Food, Activities, Travel, Objects, Symbols, Flags. Hearts and People are merged into the first section, skin tone and hair components are not shown as standalone emoji, anything missing from the order list is appended to its old group. Wired in KeyboardLayoutProvider.emojiCategories; icon map in Panels.emojiCategoryIcon accepts "Smileys & People".
- Not yet verified by a Gradle build; check CI output first if something fails.
- Perf pass: suggestions, literal and math result moved out of KeyboardUiState into SuggestionUi (KeyboardViewModel.suggestionUi), collected only by SuggestionSlots, so a suggestion update no longer re-emits uiState or recomposes the keyboard root. refreshSuggestions debounce is 30ms, 55ms during fast typing bursts (less cross-process text reads). Page-switch row stagger 22ms/340ms to 14ms/280ms.
- Not done: local text tracking to skip text reads (needs device testing), generated baseline profile (needs Macrobenchmark on a device).

## 3.25.0 (versionCode 60) - not built here, check CI
- Added KeyboardTheme.GLASS (SemoTheme.kt, ThemePicker.kt, theme_glass strings). Signing passwords can come from env vars SEMO_KS_PASS, SEMO_KS_ALIAS, SEMO_KS_KEY_PASS. Open items and what already existed are listed in TASKS_LOG.md under 3.25.0.

## 3.25.1 (versionCode 61) - not built here, check CI
- NgramModel.next uses interpolated trigram and bigram scores. See TASKS_LOG.md 3.25.1 for what was checked and what stays open.

## 3.26.0 (versionCode 62) - not built here, check CI
- NeuralLm (ONNX Runtime, ruq-5m) feeds Arabic next-word suggestions after personal words. Needs app/src/main/assets/ai/tokenizer.json (add it by hand). Details in TASKS_LOG.md 3.26.0.
