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

## Ideas for next steps
- Optional per-app or per-field source language override; swap button; translate selected text only.
