package com.semo.keyboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.ArabicLayout
import com.semo.keyboard.domain.model.EnglishLayout
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardSize
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.onboarding.OnboardingKeyboardStatus
import com.semo.keyboard.ui.onboarding.OnboardingScreen
import com.semo.keyboard.ui.onboarding.OnboardingStep
import com.semo.keyboard.ui.onboarding.StatusPill
import com.semo.keyboard.ui.theme.SemoAppTheme
import com.semo.keyboard.ui.theme.SemoPalette
import com.semo.keyboard.util.KeyboardStatusHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var learnedWordsRepository: LearnedWordsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // أندرويد 16: العرض من حافة لحافة إلزامي، فنتعامل مع الـ insets بأنفسنا
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        clipboardRepository = ClipboardRepository(applicationContext)
        learnedWordsRepository = LearnedWordsRepository(applicationContext)

        setContent {
            SemoAppTheme {
                // نص التطبيق عربي، فنثبّت الاتجاه RTL حتى لو لغة الجهاز غير ذلك
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(settingsRepository, clipboardRepository, learnedWordsRepository)
                }
            }
        }
    }
}

@Composable
private fun Root(repo: SettingsRepository, clipboardRepo: ClipboardRepository, learnedRepo: LearnedWordsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = null)

    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var forceOnboarding by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(KeyboardStatusHelper.isKeyboardEnabled(context)) }
    var isSelected by remember { mutableStateOf(KeyboardStatusHelper.isKeyboardSelected(context)) }

    // إعادة فحص الحالة كل ما رجع المستخدم للتطبيق من إعدادات النظام
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = KeyboardStatusHelper.isKeyboardEnabled(context)
                isSelected = KeyboardStatusHelper.isKeyboardSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val current = settings ?: return // بانتظار قراءة التفضيلات
    if (!current.onboardingCompleted || forceOnboarding) {
        OnboardingScreen(
            step = step,
            status = OnboardingKeyboardStatus(isEnabled, isSelected),
            onOpenEnableSettings = { openInputMethodSettings(context) },
            onOpenKeyboardPicker = { openKeyboardPicker(context) },
            onNext = { step = OnboardingStep.entries.getOrElse(step.ordinal + 1) { OnboardingStep.DONE } },
            onBack = { step = OnboardingStep.entries.getOrElse(step.ordinal - 1) { OnboardingStep.WELCOME } },
            onSkip = { step = OnboardingStep.DONE },
            onFinish = {
                forceOnboarding = false
                scope.launch { repo.setOnboardingCompleted(true) }
            }
        )
    } else {
        SettingsScreen(
            repo = repo,
            clipboardRepo = clipboardRepo,
            learnedRepo = learnedRepo,
            settings = current,
            isEnabled = isEnabled,
            isSelected = isSelected,
            onEnable = { openInputMethodSettings(context) },
            onPick = { openKeyboardPicker(context) },
            onReplay = { step = OnboardingStep.WELCOME; forceOnboarding = true }
        )
    }
}

private fun openInputMethodSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openKeyboardPicker(context: Context) {
    runCatching {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }
}

@Composable
private fun SettingsScreen(
    repo: SettingsRepository,
    clipboardRepo: ClipboardRepository,
    learnedRepo: LearnedWordsRepository,
    settings: SemoSettings,
    isEnabled: Boolean,
    isSelected: Boolean,
    onEnable: () -> Unit,
    onPick: () -> Unit,
    onReplay: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var testText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SemoPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("سيمو كيبورد", color = SemoPalette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        SemoCard {
            Text("الحالة", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            StatusPill(isEnabled, "اللوحة مفعّلة", "اللوحة غير مفعّلة")
            StatusPill(isSelected, "هي اللوحة النشطة", "مش مختارة كلوحة نشطة")
            Button(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SemoPalette.Accent, contentColor = Color.White)
            ) { Text("تفعيل اللوحة من إعدادات النظام") }
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("اختيار سيمو كيبورد كلوحة نشطة", color = SemoPalette.AccentText)
            }
        }

        SemoCard {
            Text("جرّب اللوحة هون", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("اكتب هون...", color = SemoPalette.TextHint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SemoPalette.Accent,
                    unfocusedBorderColor = SemoPalette.Stroke,
                    focusedTextColor = SemoPalette.TextPrimary,
                    unfocusedTextColor = SemoPalette.TextPrimary,
                    cursorColor = SemoPalette.Accent
                )
            )
        }

        SemoCard {
            Text("ترتيب المفاتيح", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            Text("الإنكليزي", color = SemoPalette.TextPrimary, fontSize = 15.sp)
            ChipGroup(
                options = listOf(
                    EnglishLayout.QWERTY to "QWERTY",
                    EnglishLayout.AZERTY to "AZERTY",
                    EnglishLayout.QWERTZ to "QWERTZ"
                ),
                selected = settings.englishLayout,
                onSelect = { scope.launch { repo.setEnglishLayout(it) } }
            )
            LayoutPreview(KeyboardLayoutProvider.previewRows(KeyboardLanguage.ENGLISH, settings.englishLayout, settings.arabicLayout))
            Text("العربي", color = SemoPalette.TextPrimary, fontSize = 15.sp)
            ChipGroup(
                options = listOf(
                    ArabicLayout.STANDARD to "قياسي",
                    ArabicLayout.ALPHABETIC to "أبجدي"
                ),
                selected = settings.arabicLayout,
                onSelect = { scope.launch { repo.setArabicLayout(it) } }
            )
            LayoutPreview(KeyboardLayoutProvider.previewRows(KeyboardLanguage.ARABIC, settings.englishLayout, settings.arabicLayout))
            SwitchRow("أرقام عربية هندية (١٢٣) باللوحة العربية", settings.arabicDigits) { scope.launch { repo.setArabicDigits(it) } }
            SwitchRow("صف الأرقام فوق الحروف", settings.numberRow) { scope.launch { repo.setNumberRow(it) } }
        }

        SemoCard {
            Text("المظهر", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            Text("شكل اللوحة", color = SemoPalette.TextPrimary, fontSize = 15.sp)
            ChipGroup(
                options = listOf(KeyboardStyle.IOS26 to "iOS 26", KeyboardStyle.IOS18 to "iOS 18"),
                selected = settings.style,
                onSelect = { scope.launch { repo.setStyle(it) } }
            )
            Text("الثيم", color = SemoPalette.TextPrimary, fontSize = 15.sp)
            ChipGroup(
                options = listOf(
                    ThemeMode.SYSTEM to "حسب النظام",
                    ThemeMode.LIGHT to "فاتح",
                    ThemeMode.DARK to "داكن"
                ),
                selected = settings.themeMode,
                onSelect = { scope.launch { repo.setThemeMode(it) } }
            )
            Text("ارتفاع المفاتيح", color = SemoPalette.TextPrimary, fontSize = 15.sp)
            ChipGroup(
                options = listOf(
                    KeyboardSize.SMALL to "صغير",
                    KeyboardSize.MEDIUM to "متوسط",
                    KeyboardSize.LARGE to "كبير"
                ),
                selected = settings.size,
                onSelect = { scope.launch { repo.setSize(it) } }
            )
            Text("وضع اليد الواحدة", color = SemoPalette.TextPrimary, fontSize = 15.sp)
            ChipGroup(
                options = listOf(
                    OneHandMode.OFF to "معطّل",
                    OneHandMode.LEFT to "يسار",
                    OneHandMode.RIGHT to "يمين"
                ),
                selected = settings.oneHand,
                onSelect = { scope.launch { repo.setOneHand(it) } }
            )
            SwitchRow("معاينة الحرف فوق المفتاح", settings.keyPreview) { scope.launch { repo.setKeyPreview(it) } }
        }

        SemoCard {
            Text("الكتابة", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            SwitchRow("اقتراحات الكلمات", settings.suggestionsEnabled) { scope.launch { repo.setSuggestionsEnabled(it) } }
            SwitchRow("حرف كبير تلقائي بأول الجملة", settings.autoCapitalize) { scope.launch { repo.setAutoCapitalize(it) } }
            SwitchRow("مسافتان = نقطة ومسافة", settings.doubleSpacePeriod) { scope.launch { repo.setDoubleSpacePeriod(it) } }
            SwitchRow("صوت الضغط على المفاتيح", settings.soundEnabled) { scope.launch { repo.setSoundEnabled(it) } }
            SwitchRow("الاهتزاز عند الضغط", settings.hapticEnabled) { scope.launch { repo.setHapticEnabled(it) } }
        }

        SemoCard {
            Text("الحافظة والبيانات", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            SwitchRow("حفظ سجل الحافظة", settings.clipboardEnabled) { scope.launch { repo.setClipboardEnabled(it) } }
            OutlinedButton(
                onClick = { scope.launch { clipboardRepo.clearAll() } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("مسح سجل الحافظة (مع المثبّت)", color = SemoPalette.TextSecondary) }
            OutlinedButton(
                onClick = { scope.launch { learnedRepo.clear() } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("مسح الكلمات المتعلَّمة", color = SemoPalette.TextSecondary) }
        }

        SemoCard {
            Text("الخصوصية والأذونات", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            Text(
                "سيمو كيبورد ما بتتصل بالإنترنت. سجل الحافظة والكلمات المتعلَّمة بيتخزنوا على جهازك فقط، وما بيتسجلوا بحقول كلمات المرور. الإذن الوحيد المستخدم هو الاهتزاز عند الضغط، وهو إذن عادي ما بيحتاج موافقتك.",
                color = SemoPalette.TextPrimary, fontSize = 14.sp, lineHeight = 22.sp
            )
        }

        OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("إعادة عرض دليل الإعداد", color = SemoPalette.TextSecondary)
        }
    }
}

@Composable
private fun SemoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SemoPalette.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, SemoPalette.Stroke, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SemoPalette.Accent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun <T> ChipGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SemoPalette.AccentSoft,
                    selectedLabelColor = SemoPalette.AccentText,
                    labelColor = SemoPalette.TextSecondary
                )
            )
        }
    }
}

/** معاينة مصغّرة لحروف الترتيب المختار (ثلاثة صفوف) */
@Composable
private fun LayoutPreview(rows: List<List<String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SemoPalette.Field, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // مواضع المفاتيح تُعرض LTR دائمًا كما بلوحة المفاتيح الفعلية
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.5.dp)
                                .size(width = 20.dp, height = 28.dp)
                                .background(SemoPalette.SurfaceHigh, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, color = SemoPalette.TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
