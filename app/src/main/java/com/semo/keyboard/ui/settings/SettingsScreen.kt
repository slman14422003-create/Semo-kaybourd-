package com.semo.keyboard.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.semo.keyboard.R
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.HideButtonMode
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.domain.logic.BigDictionary
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardSize
import com.semo.keyboard.domain.model.KeySpacing
import com.semo.keyboard.domain.model.NumKeyTarget
import com.semo.keyboard.domain.model.ToolbarItem
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ime.KeyClickPlayer
import com.semo.keyboard.ui.keyboard.KeyFeedback
import com.semo.keyboard.ui.theme.SemoAppTheme
import com.semo.keyboard.ui.theme.SemoPalette
import com.semo.keyboard.util.KeyboardStatusHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import com.semo.keyboard.data.ShortcutsRepository
import com.semo.keyboard.domain.model.BackspaceSpeed
import com.semo.keyboard.domain.model.HoldDelay
import com.semo.keyboard.domain.model.SpaceBarAction


/** صفحات الإعدادات: رئيسية بأقسام مرتبة، وكل قسم صفحة فرعية */
internal enum class SettingsPage { HOME, LANGUAGES, TYPING, SHORTCUTS, APPEARANCE, TOUCH, DICTIONARY, PRIVACY, ABOUT }

@Composable
internal fun SettingsScreen(
    repo: SettingsRepository,
    clipboardRepo: ClipboardRepository,
    learnedRepo: LearnedWordsRepository,
    shortcutsRepo: ShortcutsRepository,
    settings: SemoSettings,
    isEnabled: Boolean,
    isSelected: Boolean,
    onEnable: () -> Unit,
    onPick: () -> Unit,
    onReplay: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    // زر الرجوع: من «اختصارات النص» لصفحة الكتابة، ومن أي صفحة أخرى للرئيسية
    BackHandler(enabled = page != SettingsPage.HOME) {
        page = if (page == SettingsPage.SHORTCUTS) SettingsPage.TYPING else SettingsPage.HOME
    }
    val learned by learnedRepo.words.collectAsState(initial = emptyList())
    val shortcuts by shortcutsRepo.shortcuts.collectAsState(initial = emptyList())
    val home = { page = SettingsPage.HOME }

    when (page) {
        SettingsPage.HOME -> HomePage(
            settings = settings,
            shortcutCount = shortcuts.size,
            learnedCount = learned.size,
            isEnabled = isEnabled,
            isSelected = isSelected,
            onEnable = onEnable,
            onPick = onPick,
            onOpen = { page = it }
        )
        SettingsPage.LANGUAGES -> LanguagesPage(repo, settings, home)
        SettingsPage.TYPING -> TypingPage(repo, settings, shortcuts.size, home) { page = SettingsPage.SHORTCUTS }
        SettingsPage.SHORTCUTS -> ShortcutsPage(repo, shortcutsRepo, settings) { page = SettingsPage.TYPING }
        SettingsPage.APPEARANCE -> AppearancePage(repo, settings, home)
        SettingsPage.TOUCH -> TouchPage(repo, settings, home)
        SettingsPage.DICTIONARY -> DictionaryPage(learnedRepo, learned.size, home)
        SettingsPage.PRIVACY -> PrivacyPage(repo, clipboardRepo, settings, home)
        SettingsPage.ABOUT -> AboutPage(repo, onReplay, home)
    }
}

// ---------------- أسماء للعرض ----------------

private fun arabicLayoutLabel(l: ArabicLayout) = when (l) {
    ArabicLayout.STANDARD -> "قياسي"
    ArabicLayout.ALPHABETIC -> "أبجدي"
    ArabicLayout.QWERTY -> "QWERTY"
    ArabicLayout.GBOARD -> "Gboard"
}

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.SYSTEM -> "حسب النظام"
    ThemeMode.LIGHT -> "فاتح"
    ThemeMode.DARK -> "داكن"
}

private fun sizeLabel(s: KeyboardSize) = when (s) {
    KeyboardSize.SMALL -> "صغير"
    KeyboardSize.MEDIUM -> "متوسط"
    KeyboardSize.LARGE -> "كبير"
}

/** اسم عنصر شريط الأدوات وأيقونته ولونه بصفحة الإعدادات */
private fun toolbarItemInfo(item: ToolbarItem): Triple<String, Int, androidx.compose.ui.graphics.Color> = when (item) {
    ToolbarItem.EMOJI -> Triple("الإيموجي", R.drawable.ic_ios_emoji, IconOrange)
    ToolbarItem.CLIPBOARD -> Triple("الحافظة", R.drawable.ic_tool_clipboard, IconBlue)
    ToolbarItem.NUMPAD -> Triple("لوحة الأرقام", R.drawable.ic_tool_numpad, IconPurple)
    ToolbarItem.CURSOR -> Triple("التحرير وتحريك المؤشر", R.drawable.ic_tool_cursor, IconTeal)
    ToolbarItem.ONEHAND -> Triple("وضع اليد الواحدة", R.drawable.ic_tool_onehand, IconGray)
    ToolbarItem.SETTINGS -> Triple("الإعدادات", R.drawable.ic_tool_settings, IconGray)
}

private fun holdLabel(d: HoldDelay) = when (d) {
    HoldDelay.SHORT -> "قصيرة"
    HoldDelay.MEDIUM -> "متوسطة"
    HoldDelay.LONG -> "طويلة"
}

private fun backspaceLabel(b: BackspaceSpeed) = when (b) {
    BackspaceSpeed.SLOW -> "بطيء"
    BackspaceSpeed.NORMAL -> "عادي"
    BackspaceSpeed.FAST -> "سريع"
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SemoPalette.Accent,
    unfocusedBorderColor = SemoPalette.Stroke,
    focusedTextColor = SemoPalette.TextPrimary,
    unfocusedTextColor = SemoPalette.TextPrimary,
    cursorColor = SemoPalette.Accent
)

// ---------------- الصفحة الرئيسية ----------------

@Composable
private fun HomePage(
    settings: SemoSettings,
    shortcutCount: Int,
    learnedCount: Int,
    isEnabled: Boolean,
    isSelected: Boolean,
    onEnable: () -> Unit,
    onPick: () -> Unit,
    onOpen: (SettingsPage) -> Unit
) {
    var testText by remember { mutableStateOf("") }
    val typingSummary = if (!settings.suggestionsEnabled) {
        "النص التنبؤي متوقف"
    } else {
        buildList {
            add("تنبؤ")
            if (settings.nextWord) add("الكلمة التالية")
            if (settings.autoCorrect) add("استبدال تلقائي")
            if (settings.spellCheck) add("تدقيق")
            if (settings.textShortcuts && shortcutCount > 0) add("$shortcutCount اختصار")
        }.joinToString(" · ")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SemoPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("سيمو كيبورد", color = SemoPalette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("الإعدادات", color = SemoPalette.TextHint, fontSize = 13.sp)
            }
        }

        StatusCard(isEnabled, isSelected, onEnable, onPick)

        Section("جرّب اللوحة") {
            Box(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("اكتب هون...", color = SemoPalette.TextHint) },
                    colors = fieldColors()
                )
            }
        }

        Section("الإعدادات") {
            NavRow(
                "اللغات والتخطيط",
                "${settings.englishLayout.name} · ${arabicLayoutLabel(settings.arabicLayout)}" +
                    if (settings.numberRow) " · صف أرقام" else "",
                R.drawable.ic_ios_globe, IconBlue
            ) { onOpen(SettingsPage.LANGUAGES) }
            Divider()
            NavRow("الكتابة الذكية", typingSummary, R.drawable.ic_ios_emoji, IconGreen) { onOpen(SettingsPage.TYPING) }
            Divider()
            NavRow(
                "المظهر والحجم",
                "${themeLabel(settings.themeMode)} · ارتفاع ${sizeLabel(settings.size)}",
                R.drawable.ic_tool_settings, IconGray
            ) { onOpen(SettingsPage.APPEARANCE) }
            Divider()
            NavRow(
                "اللمس والصوت والاهتزاز",
                "ضغط مطوّل ${holdLabel(settings.holdDelay)} · حذف ${backspaceLabel(settings.backspaceSpeed)}",
                R.drawable.ic_tool_cursor, IconOrange
            ) { onOpen(SettingsPage.TOUCH) }
            Divider()
            NavRow(
                "القاموس والكلمات",
                "$learnedCount كلمة متعلَّمة · قاموس مدمج + تنزيل اختياري",
                R.drawable.ic_ios_backspace, IconPurple
            ) { onOpen(SettingsPage.DICTIONARY) }
            Divider()
            NavRow(
                "الحافظة والخصوصية",
                if (settings.clipboardEnabled) "سجل الحافظة مفعّل" else "سجل الحافظة متوقف",
                R.drawable.ic_tool_clipboard, IconTeal
            ) { onOpen(SettingsPage.PRIVACY) }
        }

        Section {
            NavRow("حول وإعادة الضبط", "الإصدار والدليل وإعادة الإعدادات للافتراضي", R.drawable.ic_tool_settings, IconPink) {
                onOpen(SettingsPage.ABOUT)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------- اللغات والتخطيط ----------------

@Composable
private fun LanguagesPage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    SubPage("اللغات والتخطيط", onBack) {
        Section("ترتيب المفاتيح") {
            Block("الإنكليزي", R.drawable.ic_ios_globe, IconBlue) {
                Segmented(
                    options = listOf(
                        EnglishLayout.QWERTY to "QWERTY",
                        EnglishLayout.AZERTY to "AZERTY",
                        EnglishLayout.QWERTZ to "QWERTZ"
                    ),
                    selected = settings.englishLayout,
                    onSelect = { scope.launch { repo.setEnglishLayout(it) } }
                )
                LayoutPreview(KeyboardLayoutProvider.previewRows(KeyboardLanguage.ENGLISH, settings.englishLayout, settings.arabicLayout))
            }
            Divider()
            Block("العربي", R.drawable.ic_ios_globe, IconGreen) {
                Segmented(
                    options = listOf(
                        ArabicLayout.STANDARD to "قياسي",
                        ArabicLayout.ALPHABETIC to "أبجدي",
                        ArabicLayout.QWERTY to "QWERTY",
                        ArabicLayout.GBOARD to "Gboard"
                    ),
                    selected = settings.arabicLayout,
                    onSelect = { scope.launch { repo.setArabicLayout(it) } }
                )
                LayoutPreview(KeyboardLayoutProvider.previewRows(KeyboardLanguage.ARABIC, settings.englishLayout, settings.arabicLayout))
            }
        }
        Section("الأرقام") {
            SwitchRow("أرقام عربية هندية (١٢٣)", R.drawable.ic_ios_shift, IconOrange, settings.arabicDigits) {
                scope.launch { repo.setArabicDigits(it) }
            }
            Divider()
            SwitchRow(
                "صف الأرقام فوق الحروف", R.drawable.ic_ios_shift, IconPurple, settings.numberRow,
                subtitle = "يضيف صفًا للأرقام دائمًا فوق الحروف"
            ) { scope.launch { repo.setNumberRow(it) } }
        }
    }
}

// ---------------- الكتابة الذكية ----------------

@Composable
private fun TypingPage(
    repo: SettingsRepository,
    settings: SemoSettings,
    shortcutCount: Int,
    onBack: () -> Unit,
    onOpenShortcuts: () -> Unit
) {
    val scope = rememberCoroutineScope()
    SubPage("الكتابة الذكية", onBack) {
        Section("التنبؤ والتصحيح") {
            SwitchRow(
                "النص التنبؤي", R.drawable.ic_ios_emoji, IconBlue, settings.suggestionsEnabled,
                subtitle = "يقترح إكمال الكلمة أثناء الكتابة بشريط الاقتراحات"
            ) { scope.launch { repo.setSuggestionsEnabled(it) } }
            Divider()
            SwitchRow(
                "توقّع الكلمة التالية", R.drawable.ic_tool_cursor, IconGreen, settings.nextWord,
                subtitle = "يقترح الكلمة المتوقعة بعد ما تكتب، ويتعلّم من أسلوبك بالكتابة"
            ) { scope.launch { repo.setNextWord(it) } }
            Divider()
            SwitchRow(
                "استبدال تلقائي", R.drawable.ic_ios_backspace, IconTeal, settings.autoCorrect,
                subtitle = "يصحّح الأخطاء الشائعة (dont ← don't، الى ← إلى) والأخطاء الإملائية الواضحة بالإنكليزي"
            ) { scope.launch { repo.setAutoCorrect(it) } }
            Divider()
            SwitchRow(
                "تدقيق إملائي", R.drawable.ic_ios_shift, IconOrange, settings.spellCheck,
                subtitle = "يقترح الكلمة الصحيحة بالشريط لو الكلمة غير موجودة بالقاموس (مثل recieve)"
            ) { scope.launch { repo.setSpellCheck(it) } }
            Divider()
            SwitchRow(
                "اقتراح إيموجي", R.drawable.ic_ios_emoji, IconPink, settings.suggestEmojis,
                subtitle = "يقترح إيموجي مناسب للكلمة مثل love ← ❤️"
            ) { scope.launch { repo.setSuggestEmojis(it) } }
        }
        Section("اختصارات النص") {
            NavRow(
                "اختصارات النص",
                if (shortcutCount == 0) "أضف اختصارًا يتحوّل لعبارة كاملة (omw ← On my way!)" else "$shortcutCount اختصار",
                R.drawable.ic_tool_clipboard, IconPurple,
                onOpenShortcuts
            )
        }
        Section("خيارات الكتابة") {
            SwitchRow("حرف كبير تلقائي بأول الجملة", R.drawable.ic_ios_shift, IconGreen, settings.autoCapitalize) {
                scope.launch { repo.setAutoCapitalize(it) }
            }
            Divider()
            SwitchRow("مسافتان = نقطة ومسافة", R.drawable.ic_tool_cursor, IconOrange, settings.doubleSpacePeriod) {
                scope.launch { repo.setDoubleSpacePeriod(it) }
            }
            Divider()
            SwitchRow(
                "ناتج العمليات الحسابية", R.drawable.ic_tool_settings, IconPink, settings.mathResults,
                subtitle = "اكتب 60*30+20 فيظهر الناتج بالشريط"
            ) { scope.launch { repo.setMathResults(it) } }
        }
        Hint("الاقتراحات والتصحيح والتعلّم ما بتشتغل بحقول كلمات المرور. الكلمات المتعلَّمة بتنخزن على جهازك فقط، وبتقدر تمسحها من «القاموس والكلمات».")
    }
}

@Composable
private fun ShortcutsPage(
    repo: SettingsRepository,
    shortcutsRepo: ShortcutsRepository,
    settings: SemoSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val list by shortcutsRepo.shortcuts.collectAsState(initial = emptyList())
    var abbr by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SubPage("اختصارات النص", onBack) {
        Section {
            SwitchRow(
                "تفعيل اختصارات النص", R.drawable.ic_tool_clipboard, IconPurple, settings.textShortcuts,
                subtitle = "اكتب الاختصار ثم مسافة فيتحوّل للنص الكامل. تقدر تتراجع بزر الحذف مباشرة"
            ) { scope.launch { repo.setTextShortcuts(it) } }
        }
        Section("إضافة اختصار") {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = abbr,
                    onValueChange = { abbr = it.take(15); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    placeholder = { Text("الاختصار (حروف فقط، مثل omw)", color = SemoPalette.TextHint) },
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = expansion,
                    onValueChange = { expansion = it.take(200); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("النص الكامل (مثل On my way!)", color = SemoPalette.TextHint) },
                    colors = fieldColors()
                )
                val message = error
                if (message != null) {
                    Text(message, color = DangerRed, fontSize = 12.sp)
                }
                SolidButton("إضافة", abbr.isNotBlank() && expansion.isNotBlank(), Modifier.fillMaxWidth()) {
                    scope.launch {
                        val ok = shortcutsRepo.add(abbr, expansion)
                        if (ok) {
                            abbr = ""
                            expansion = ""
                            error = null
                        } else {
                            error = "الاختصار لازم يكون حروف فقط (حتى 15 حرف) والنص غير فارغ. وما بيشتغل قبل فك قفل الجهاز."
                        }
                    }
                }
            }
        }
        Section("اختصاراتك (${list.size})") {
            if (list.isEmpty()) {
                Text(
                    "ما في اختصارات بعد. جرّب: omw ← On my way!  أو  عع ← عن إذنك",
                    color = SemoPalette.TextHint,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(14.dp)
                )
            } else {
                list.forEachIndexed { index, item ->
                    if (index > 0) Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(item.abbr, color = SemoPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(item.expansion, color = SemoPalette.TextHint, fontSize = 13.sp, maxLines = 2)
                        }
                        Text(
                            "حذف",
                            color = DangerRed,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { scope.launch { shortcutsRepo.remove(item.abbr) } }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
        Hint("الاختصار لازم يكون حروفًا فقط، والتطابق ما بيفرّق بين الأحرف الكبيرة والصغيرة.")
    }
}

// ---------------- المظهر والحجم ----------------

@Composable
private fun AppearancePage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    SubPage("المظهر والحجم", onBack) {
        Section("الشكل") {
            Block("الثيم", R.drawable.ic_tool_settings, IconGray) {
                Segmented(
                    options = listOf(ThemeMode.SYSTEM to "حسب النظام", ThemeMode.LIGHT to "فاتح", ThemeMode.DARK to "داكن"),
                    selected = settings.themeMode,
                    onSelect = { scope.launch { repo.setThemeMode(it) } }
                )
            }
            Divider()
            Block("ارتفاع المفاتيح", R.drawable.ic_tool_cursor, IconTeal) {
                Segmented(
                    options = listOf(KeyboardSize.SMALL to "صغير", KeyboardSize.MEDIUM to "متوسط", KeyboardSize.LARGE to "كبير"),
                    selected = settings.size,
                    onSelect = { scope.launch { repo.setSize(it) } }
                )
            }
            Divider()
            Block("المسافة بين المفاتيح", R.drawable.ic_tool_cursor, IconBlue) {
                Segmented(
                    options = listOf(KeySpacing.TIGHT to "ضيّقة", KeySpacing.NORMAL to "عادية", KeySpacing.WIDE to "واسعة"),
                    selected = settings.keySpacing,
                    onSelect = { scope.launch { repo.setKeySpacing(it) } }
                )
            }
            Divider()
            Block("زر 123", R.drawable.ic_tool_numpad, IconPurple) {
                Segmented(
                    options = listOf(NumKeyTarget.NUMPAD to "لوحة أرقام 3×4", NumKeyTarget.SYMBOLS to "صفحة الرموز"),
                    selected = settings.numKeyTarget,
                    onSelect = { scope.launch { repo.setNumKeyTarget(it) } }
                )
                Hint("لوحة الأرقام تظهر تلقائيًا بحقول الأرقام والهاتف، وبباقي الحقول يفتح 123 الوجهة المختارة. الضغطة المطوّلة على 123 تفتح الوجهة الأخرى، ولوحة الأرقام متاحة من شريط الأدوات أيضًا. رموز مثل = % * # [ ] تلاقيها بالضغط المطوّل على مفاتيح اللوحة.")
            }
            Divider()
            Block("وضع اليد الواحدة", R.drawable.ic_tool_onehand, IconOrange) {
                Segmented(
                    options = listOf(OneHandMode.OFF to "معطّل", OneHandMode.LEFT to "يسار", OneHandMode.RIGHT to "يمين"),
                    selected = settings.oneHand,
                    onSelect = { scope.launch { repo.setOneHand(it) } }
                )
            }
        }
        Section("شريط الأدوات") {
            // المفعّلة أولًا بترتيبها المحفوظ، ثم المعطّلة
            val enabled = settings.toolbarItems
            val order = enabled + ToolbarItem.values().filter { it !in enabled }
            order.forEachIndexed { index, item ->
                val on = item in enabled
                val info = toolbarItemInfo(item)
                if (index > 0) Divider()
                ToolbarItemRow(
                    title = info.first,
                    iconRes = info.second,
                    tint = info.third,
                    checked = on,
                    canUp = on && index > 0,
                    canDown = on && index < enabled.size - 1,
                    // لازم يبقى عنصر واحد على الأقل
                    canToggle = !(on && enabled.size == 1),
                    onToggle = { scope.launch { repo.setToolbarItems(order.filter { (it in enabled) != (it == item) }) } },
                    onUp = {
                        val l = order.toMutableList()
                        java.util.Collections.swap(l, index, index - 1)
                        scope.launch { repo.setToolbarItems(l.filter { it in enabled }) }
                    },
                    onDown = {
                        val l = order.toMutableList()
                        java.util.Collections.swap(l, index, index + 1)
                        scope.launch { repo.setToolbarItems(l.filter { it in enabled }) }
                    }
                )
            }
            Hint("اختر العناصر اللي بدك تظهر لما تفتح شريط الأدوات (السهم الصغير بطرف اللوحة)، ورتّبها بالأسهم.")
        }
        Section("الصف السفلي") {
            // دائرة إخفاء اللوحة بالصف السفلي (يمين) بمقابل دائرة الكرة الأرضية (يسار)
            Block("دائرة إخفاء الكيبورد", R.drawable.ic_ios_hide_keyboard, IconTeal) {
                Segmented(
                    options = listOf(
                        HideButtonMode.RING to "دائرة",
                        HideButtonMode.ARROW to "دائرة + سهم",
                        HideButtonMode.OFF to "بدون"
                    ),
                    selected = settings.hideButtonMode,
                    onSelect = { scope.launch { repo.setHideButtonMode(it) } }
                )
                Text(
                    "«دائرة + سهم» هو الوضع المناسب مع رفع الدائرتين. على سامسونج أطفئ من إعدادات لوحة المفاتيح خيار «Show button to hide keyboard» كي لا يظهر سهم النظام مرتين. وضع «دائرة» يرسم الدائرة خلف سهم النظام وهو مناسب فقط لو الرفع = 0.",
                    color = SemoPalette.TextHint,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            Divider()
            var raise by remember(settings.bottomRaiseDp) { mutableFloatStateOf(settings.bottomRaiseDp) }
            SliderRow(
                title = "رفع الدائرتين عن أسفل الشاشة",
                valueLabel = if (raise.roundToInt() == 0) "ملاصقتان" else "${raise.roundToInt()}",
                value = raise,
                range = 0f..40f,
                steps = 19,
                onChange = { raise = it },
                onFinish = { scope.launch { repo.setBottomRaise(raise.roundToInt().toFloat()) } }
            )
            if (Build.VERSION.SDK_INT >= 35) {
                Divider()
                var globeOffset by remember(settings.globeOffsetDp) { mutableFloatStateOf(settings.globeOffsetDp) }
                SliderRow(
                    title = "ارتفاع زر الكرة الأرضية",
                    valueLabel = if (globeOffset.roundToInt() == 0) "تلقائي" else "${globeOffset.roundToInt()}",
                    value = globeOffset,
                    range = -8f..8f,
                    steps = 15,
                    onChange = { globeOffset = it },
                    onFinish = { scope.launch { repo.setGlobeOffset(globeOffset.roundToInt().toFloat()) } }
                )
                Hint("حرّكه لفوق أو لتحت حتى تصير الأيقونة بمحاذاة زر إخفاء الكيبورد تمامًا. الوضع التلقائي مضبوط على منتصف شريط التنقل.")
            }
        }
    }
}

// ---------------- اللمس والصوت والاهتزاز ----------------

@Composable
private fun TouchPage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val player = remember { KeyClickPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    SubPage("اللمس والصوت والاهتزاز", onBack) {
        Section("الكتابة والإيماءات") {
            SwitchRow(
                "الكتابة بالسحب على الحروف", R.drawable.ic_tool_cursor, IconPurple, settings.swipeTyping,
                subtitle = "مرّر إصبعك على الحروف لكتابة الكلمة بحركة وحدة"
            ) { scope.launch { repo.setSwipeTyping(it) } }
            Divider()
            SwitchRow("معاينة الحرف فوق المفتاح", R.drawable.ic_ios_emoji, IconBlue, settings.keyPreview) {
                scope.launch { repo.setKeyPreview(it) }
            }
            Divider()
            SwitchRow(
                "إبقاء لوحة الرموز مفتوحة", R.drawable.ic_ios_shift, IconGreen, settings.keepSymbolPanel,
                subtitle = "بعد كتابة رمز تبقى لوحة الرموز ظاهرة. لو متوقف ترجع اللوحة للحروف"
            ) { scope.launch { repo.setKeepSymbolPanel(it) } }
        }
        Section("الضغط المطوّل") {
            Block("مهلة الضغط المطوّل", R.drawable.ic_tool_cursor, IconTeal) {
                Segmented(
                    options = listOf(
                        HoldDelay.SHORT to "قصيرة 0.2ث",
                        HoldDelay.MEDIUM to "متوسطة 0.3ث",
                        HoldDelay.LONG to "طويلة 0.5ث"
                    ),
                    selected = settings.holdDelay,
                    onSelect = { scope.launch { repo.setHoldDelay(it) } }
                )
                Text(
                    "المدة اللازمة لفتح بدائل الحروف (مثل الهمزات والتشكيل) وتفعيل الضغطة المطوّلة على المسافة.",
                    color = SemoPalette.TextHint,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            Divider()
            Block("سرعة الحذف", R.drawable.ic_ios_backspace, IconPink) {
                Segmented(
                    options = listOf(
                        BackspaceSpeed.SLOW to "بطيء",
                        BackspaceSpeed.NORMAL to "عادي",
                        BackspaceSpeed.FAST to "سريع"
                    ),
                    selected = settings.backspaceSpeed,
                    onSelect = { scope.launch { repo.setBackspaceSpeed(it) } }
                )
            }
            Divider()
            Block("ضغطة مطوّلة على المسافة", R.drawable.ic_tool_cursor, IconPurple) {
                Segmented(
                    options = listOf(
                        SpaceBarAction.CURSOR to "تحريك المؤشر",
                        SpaceBarAction.SWITCH_LANGUAGE to "تبديل اللغة",
                        SpaceBarAction.NONE to "بدون"
                    ),
                    selected = settings.spaceBarAction,
                    onSelect = { scope.launch { repo.setSpaceBarAction(it) } }
                )
            }
        }
        Section("الصوت والاهتزاز") {
            SwitchRow("صوت الضغط على المفاتيح", R.drawable.ic_ios_mic, IconPink, settings.soundEnabled) {
                scope.launch { repo.setSoundEnabled(it) }
            }
            Divider()
            // مستوى الصوت: نحرّكه محليًا ونحفظ عند رفع الإصبع، ونشغّل نقرة تجريبية بالمستوى الجديد
            var volume by remember(settings.soundVolume) { mutableFloatStateOf(settings.soundVolume) }
            SliderRow(
                title = "مستوى الصوت",
                valueLabel = "${(volume * 100).roundToInt()}%",
                value = volume,
                range = 0f..1f,
                steps = 0,
                enabled = settings.soundEnabled,
                onChange = { volume = it },
                onFinish = {
                    scope.launch { repo.setSoundVolume(volume) }
                    player.play(KeyFeedback.STANDARD, volume)
                }
            )
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton("حرف", Modifier.weight(1f)) { player.play(KeyFeedback.STANDARD, volume) }
                PillButton("حذف", Modifier.weight(1f)) { player.play(KeyFeedback.DELETE, volume) }
                PillButton("مسافة", Modifier.weight(1f)) { player.play(KeyFeedback.MODIFIER, volume) }
            }
            Divider()
            SwitchRow("الاهتزاز عند الضغط", R.drawable.ic_tool_onehand, IconOrange, settings.hapticEnabled) {
                scope.launch { repo.setHapticEnabled(it) }
            }
        }
    }
}

// ---------------- القاموس والكلمات ----------------

@Composable
private fun DictionaryPage(learnedRepo: LearnedWordsRepository, learnedCount: Int, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    SubPage("القاموس والكلمات", onBack) {
        Section("القاموس") {
            DictionaryBlock()
        }
        Section("الكلمات المتعلَّمة") {
            Text(
                "$learnedCount كلمة تعلّمتها اللوحة من كتابتك. الأكثر استعمالًا بتظهر أول بالاقتراحات، وكمان بتتعلّم أي كلمة بتجي بعد أي كلمة لتوقّع الكلمة التالية.",
                color = SemoPalette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(14.dp)
            )
            Divider()
            ActionRow("مسح الكلمات المتعلَّمة وتوقّعات الكلمة التالية", DangerRed) {
                scope.launch { learnedRepo.clear() }
            }
        }
        Hint("ما بيتعلّم شي بحقول كلمات المرور. كل شي بيتخزن على جهازك فقط.")
    }
}

// ---------------- الحافظة والخصوصية ----------------

@Composable
private fun PrivacyPage(
    repo: SettingsRepository,
    clipboardRepo: ClipboardRepository,
    settings: SemoSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    SubPage("الحافظة والخصوصية", onBack) {
        Section("الحافظة") {
            SwitchRow("حفظ سجل الحافظة", R.drawable.ic_tool_clipboard, IconBlue, settings.clipboardEnabled) {
                scope.launch { repo.setClipboardEnabled(it) }
            }
            Divider()
            ActionRow("مسح سجل الحافظة (مع المثبّت)", DangerRed) { scope.launch { clipboardRepo.clearAll() } }
        }
        Section("الخصوصية والأذونات") {
            Text(
                "خدمة لوحة المفاتيح نفسها ما بتتصل بالإنترنت أبدًا. الاتصال الوحيد هو لما تضغط بنفسك «تنزيل القاموس» بصفحة «القاموس والكلمات» (ملف كلمات من GitHub). سجل الحافظة (نصوص وصور) والكلمات المتعلَّمة والاختصارات بيتخزنوا على جهازك فقط، وما بيتسجلوا بحقول كلمات المرور. الأذونات: الاهتزاز عند الضغط، والإنترنت لزر التنزيل فقط، وكلاهما عادي ما بيحتاج موافقتك.",
                color = SemoPalette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

// ---------------- حول وإعادة الضبط ----------------

@Composable
private fun AboutPage(repo: SettingsRepository, onReplay: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var confirmReset by remember { mutableStateOf(false) }

    SubPage("حول وإعادة الضبط", onBack) {
        Section {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("سيمو كيبورد", color = SemoPalette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("الإصدار $version", color = SemoPalette.TextHint, fontSize = 13.sp)
                }
            }
            Divider()
            ActionRow("إعادة عرض دليل الإعداد", onClick = onReplay)
        }
        Section("إعادة الضبط") {
            ActionRow(
                if (confirmReset) "اضغط مرة ثانية للتأكيد" else "إعادة الإعدادات للافتراضي",
                DangerRed
            ) {
                if (!confirmReset) {
                    confirmReset = true
                } else {
                    scope.launch { repo.resetToDefaults() }
                    confirmReset = false
                }
            }
        }
        Hint("بيرجّع كل الإعدادات (التخطيط، المظهر، اللمس، الصوت…) للقيم الافتراضية. الكلمات المتعلَّمة والاختصارات وسجل الحافظة ما بتنمسح.")
    }
}
