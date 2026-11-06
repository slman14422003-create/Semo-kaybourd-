package com.semo.keyboard.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.semo.keyboard.R
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.AutoCorrectLevel
import com.semo.keyboard.domain.model.BlurPreset
import com.semo.keyboard.domain.model.BlurTintMode
import com.semo.keyboard.domain.model.LearnSpeed
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.L
import com.semo.keyboard.ui.theme.FrostSpec
import com.semo.keyboard.ui.theme.drawFrostKey
import com.semo.keyboard.ui.theme.frostPanel
import com.semo.keyboard.ui.theme.semoColors
import com.semo.keyboard.ui.theme.withBlur
import com.semo.keyboard.util.BlurSupport
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun AutoCorrectPage(
    repo: SettingsRepository,
    settings: SemoSettings,
    onBack: () -> Unit,
    onOpenDictionary: () -> Unit
) {
    val scope = rememberCoroutineScope()
    SubPage(L("Auto-correct and learning"), onBack) {
        Section(L("Auto-correct")) {
            SwitchRow(
                L("Auto-correct"), R.drawable.ic_set_spell, IconTeal, settings.autoCorrect,
                subtitle = L("Replaces common mistakes and obvious typos when you finish a word")
            ) { scope.launch { repo.setAutoCorrect(it) } }
            Divider()
            Block(L("Strength"), R.drawable.ic_set_spell, IconTeal) {
                Segmented(
                    options = listOf(
                        AutoCorrectLevel.LIGHT to L("Light"),
                        AutoCorrectLevel.BALANCED to L("Balanced"),
                        AutoCorrectLevel.STRONG to L("Strong")
                    ),
                    selected = settings.autoCorrectLevel,
                    onSelect = { scope.launch { repo.setAutoCorrectLevel(it) } }
                )
            }
            Hint(
                when (settings.autoCorrectLevel) {
                    AutoCorrectLevel.LIGHT -> L("Only fixed shortcuts and fixes you taught the keyboard. No spelling guesses")
                    AutoCorrectLevel.BALANCED -> L("Shortcuts, taught fixes and clear single-letter typos of common words")
                    AutoCorrectLevel.STRONG -> L("Also fixes rarer words and slightly bigger typos. Use with care")
                }
            )
            Divider()
            SwitchRow(
                L("Correct Arabic words"), R.drawable.ic_set_text, IconBlue, settings.autoCorrectArabic
            ) { scope.launch { repo.setAutoCorrectArabic(it) } }
            Divider()
            SwitchRow(
                L("Correct Latin-script words"), R.drawable.ic_set_text, IconBlue, settings.autoCorrectLatin
            ) { scope.launch { repo.setAutoCorrectLatin(it) } }
            Divider()
            var minDraft by remember(settings.autoCorrectMinLength) { mutableStateOf(settings.autoCorrectMinLength.toFloat()) }
            SliderRow(
                title = L("Minimum word length for spelling guesses"),
                valueLabel = "${minDraft.roundToInt()}",
                value = minDraft,
                range = 2f..6f,
                steps = 3,
                onChange = { minDraft = it },
                onFinish = { scope.launch { repo.setAutoCorrectMinLength(minDraft.roundToInt()) } }
            )
            Divider()
            SwitchRow(
                L("Correct when typing punctuation"), R.drawable.ic_set_text, IconPurple, settings.correctOnPunctuation,
                subtitle = L("Also applies the fix when you type a period, comma or similar mark")
            ) { scope.launch { repo.setCorrectOnPunctuation(it) } }
        }
        Section(L("Protection")) {
            SwitchRow(
                L("Never replace learned words"), R.drawable.ic_set_lock, IconGreen, settings.protectLearnedWords,
                subtitle = L("Words you have typed and kept are never changed by auto-correct or auto-complete")
            ) { scope.launch { repo.setProtectLearned(it) } }
            Divider()
            SwitchRow(
                L("Keep names untouched"), R.drawable.ic_set_lock, IconGreen, settings.protectNames,
                subtitle = L("Capitalized words in the middle of a sentence are left as typed")
            ) { scope.launch { repo.setProtectNames(it) } }
            Hint(L("Tip: press backspace right after a correction to restore your word. It will not be corrected again"))
        }
        Section(L("Learning")) {
            SwitchRow(
                L("Learn new words"), R.drawable.ic_set_dictionary, IconOrange, settings.learnWordsEnabled,
                subtitle = L("Remembers words you type so they are suggested and never corrected")
            ) { scope.launch { repo.setLearnWords(it) } }
            Divider()
            Block(L("Learning speed"), R.drawable.ic_set_dictionary, IconOrange) {
                Segmented(
                    options = listOf(
                        LearnSpeed.CAREFUL to L("Careful"),
                        LearnSpeed.NORMAL to L("Normal"),
                        LearnSpeed.FAST to L("Fast")
                    ),
                    selected = settings.learnSpeed,
                    onSelect = { scope.launch { repo.setLearnSpeed(it) } }
                )
            }
            Hint(
                when (settings.learnSpeed) {
                    LearnSpeed.CAREFUL -> L("A new word is learned after you type it twice")
                    LearnSpeed.NORMAL -> L("Clear new words are learned at once, likely typos after two uses")
                    LearnSpeed.FAST -> L("Every new word is learned immediately")
                }
            )
            Divider()
            SwitchRow(
                L("Learn word pairs"), R.drawable.ic_set_text, IconBlue, settings.learnNextWords,
                subtitle = L("Improves next-word suggestions from your writing")
            ) { scope.launch { repo.setLearnNextWords(it) } }
            Divider()
            ActionRow(L("Manage learned words"), onClick = onOpenDictionary)
        }
    }
}

@Composable
internal fun BlurPage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { BlurSupport.attach(context.applicationContext) }
    val supported = BlurSupport.enabled
    SubPage(L("Blur and glass"), onBack) {
        Section(null) {
            Box(Modifier.padding(10.dp)) { BlurPreview(settings) }
        }
        Section(L("Blur")) {
            SwitchRow(
                L("Background blur"), R.drawable.ic_set_theme, IconBlue, settings.blurEnabled,
                subtitle = L("Blurs the app behind the keyboard like frosted glass")
            ) { scope.launch { repo.setBlurEnabled(it) } }
            Hint(
                when {
                    !BlurSupport.available() -> L("Real blur needs Android 12 or newer. A tinted panel is used instead")
                    supported -> L("Window blur is available on this device")
                    else -> L("Window blur is turned off by the system (battery saver or developer setting). A tinted panel is used instead")
                }
            )
            Divider()
            Block(L("Style"), R.drawable.ic_set_theme, IconBlue) {
                Segmented(
                    options = listOf(
                        BlurPreset.CLEAR to L("Clear"),
                        BlurPreset.THIN to L("Thin"),
                        BlurPreset.REGULAR to L("Regular"),
                        BlurPreset.THICK to L("Thick")
                    ),
                    selected = settings.blurPreset,
                    onSelect = { scope.launch { repo.applyBlurPreset(it) } }
                )
            }
        }
        Section(L("Fine tuning")) {
            var radiusDraft by remember(settings.blurRadius) { mutableStateOf(settings.blurRadius.toFloat()) }
            SliderRow(
                title = L("Blur radius"),
                valueLabel = "${radiusDraft.roundToInt()}",
                value = radiusDraft,
                range = 0f..150f,
                steps = 29,
                enabled = settings.blurEnabled,
                onChange = { radiusDraft = it },
                onFinish = { scope.launch { repo.setBlurRadius(radiusDraft.roundToInt()) } }
            )
            var tintDraft by remember(settings.blurTint) { mutableStateOf(settings.blurTint) }
            SliderRow(
                title = L("Tint strength"),
                valueLabel = "${(tintDraft * 100).roundToInt()}%",
                value = tintDraft,
                range = 0f..1f,
                steps = 19,
                enabled = settings.blurEnabled,
                onChange = { tintDraft = it },
                onFinish = { scope.launch { repo.setBlurTint(tintDraft) } }
            )
            var keyDraft by remember(settings.glassOpacity) { mutableStateOf(settings.glassOpacity) }
            SliderRow(
                title = L("Key opacity"),
                valueLabel = "${(keyDraft * 100).roundToInt()}%",
                value = keyDraft,
                range = 0.35f..1f,
                steps = 12,
                onChange = { keyDraft = it },
                onFinish = { scope.launch { repo.setGlassOpacity(keyDraft) } }
            )
            var sheenDraft by remember(settings.blurSheen) { mutableStateOf(settings.blurSheen) }
            SliderRow(
                title = L("Top sheen"),
                valueLabel = "${(sheenDraft * 100).roundToInt()}%",
                value = sheenDraft,
                range = 0f..1f,
                steps = 19,
                enabled = settings.blurEnabled,
                onChange = { sheenDraft = it },
                onFinish = { scope.launch { repo.setBlurSheen(sheenDraft) } }
            )
            var borderDraft by remember(settings.blurBorder) { mutableStateOf(settings.blurBorder) }
            SliderRow(
                title = L("Edge highlight"),
                valueLabel = "${(borderDraft * 100).roundToInt()}%",
                value = borderDraft,
                range = 0f..1f,
                steps = 19,
                enabled = settings.blurEnabled,
                onChange = { borderDraft = it },
                onFinish = { scope.launch { repo.setBlurBorder(borderDraft) } }
            )
            Divider()
            Block(L("Tint color"), R.drawable.ic_set_theme, IconPurple) {
                Segmented(
                    options = listOf(
                        BlurTintMode.AUTO to L("Theme"),
                        BlurTintMode.ACCENT to L("Accent"),
                        BlurTintMode.LIGHTEN to L("Lighter"),
                        BlurTintMode.DARKEN to L("Darker")
                    ),
                    selected = settings.blurTintMode,
                    onSelect = { scope.launch { repo.setBlurTintMode(it) } }
                )
            }
        }
        Section(L("Options")) {
            SwitchRow(
                L("Frosted keys"), R.drawable.ic_set_theme, IconTeal, settings.blurKeys,
                subtitle = L("Adds a glass edge and sheen to each key")
            ) { scope.launch { repo.setBlurKeys(it) } }
            Divider()
            SwitchRow(
                L("Solid fallback"), R.drawable.ic_set_lock, IconGray, settings.blurFallbackSolid,
                subtitle = L("When blur is unavailable, use a nearly solid panel so text stays readable")
            ) { scope.launch { repo.setBlurFallbackSolid(it) } }
            Divider()
            ActionRow(L("Reset blur settings")) {
                scope.launch {
                    repo.applyBlurPreset(BlurPreset.REGULAR)
                    repo.setBlurEnabled(false)
                    repo.setBlurTintMode(BlurTintMode.AUTO)
                    repo.setBlurKeys(true)
                    repo.setBlurFallbackSolid(true)
                    repo.setGlassOpacity(1f)
                    repo.setPanelOpacity(1f)
                }
            }
        }
    }
}

@Composable
private fun BlurPreview(settings: SemoSettings) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = remember(settings.style, dark, settings.keyboardTheme) { semoColors(settings.style, dark, settings.keyboardTheme) }
    val active = settings.blurEnabled
    val colors = remember(base, active, settings.glassOpacity, settings.blurTint, settings.blurTintMode, dark) {
        if (active) base.withBlur(settings.glassOpacity, settings.blurTint, settings.blurTintMode, dark) else base
    }
    val frost = remember(active, settings.blurKeys, settings.blurBorder, settings.blurSheen) {
        FrostSpec(active, settings.blurKeys, settings.blurBorder, settings.blurSheen, Color.White)
    }
    val blurDp = if (active) (settings.blurRadius / 4f).coerceAtLeast(0f).dp else 0.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurDp)
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF3A7BD5), Color(0xFFB06AB3), Color(0xFFFF9966))))
                    val r = size.minDimension / 3f
                    drawCircle(Color(0xFFFFE259), r * 0.8f, Offset(size.width * 0.2f, size.height * 0.35f))
                    drawCircle(Color(0xFF34C759), r * 0.7f, Offset(size.width * 0.55f, size.height * 0.55f))
                    drawCircle(Color(0xFFFF375F), r * 0.9f, Offset(size.width * 0.85f, size.height * 0.3f))
                    drawCircle(Color(0xFF30B0C7), r * 0.6f, Offset(size.width * 0.4f, size.height * 0.2f))
                }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(128.dp)
                .frostPanel(colors.panel, frost)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val counts = listOf(10, 9, 7)
            counts.forEach { n ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(n) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .drawBehind {
                                    val radius = 5.dp.toPx()
                                    drawRoundRect(colors.key, cornerRadius = CornerRadius(radius))
                                    drawFrostKey(frost, radius)
                                }
                        )
                    }
                }
            }
        }
    }
}
