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
