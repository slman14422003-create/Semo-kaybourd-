package com.semo.keyboard.ui

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
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ime.KeyClickPlayer
import com.semo.keyboard.ui.keyboard.KeyFeedback
import com.semo.keyboard.ui.onboarding.OnboardingKeyboardStatus
import com.semo.keyboard.ui.onboarding.OnboardingScreen
import com.semo.keyboard.ui.onboarding.OnboardingStep
import com.semo.keyboard.ui.theme.SemoAppTheme
import com.semo.keyboard.ui.theme.SemoPalette
import com.semo.keyboard.util.KeyboardStatusHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // نفحص الحالة باستمرار طالما التطبيق ظاهر. كان الفحص يتم فقط عند العودة من الإعدادات،
    // فلا يتحدّث بعد نافذة اختيار اللوحة (لا تُوقف الـ Activity) فتبقى «غير مفعّلة» رغم أنها تعمل.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                isEnabled = KeyboardStatusHelper.isKeyboardEnabled(context)
                isSelected = KeyboardStatusHelper.isKeyboardSelected(context)
                delay(600)
            }
        }
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

/** يفتح إعدادات لوحات المفاتيح، وعند فشلها (بعض الأجهزة) يجرّب شاشات بديلة قبل الاستسلام */
private fun openInputMethodSettings(context: Context) {
    val attempts = listOf(
        Settings.ACTION_INPUT_METHOD_SETTINGS,
        Settings.ACTION_SETTINGS
    )
    for (action in attempts) {
        val ok = runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return
    }
    Toast.makeText(context, "ما قدرت أفتح الإعدادات. افتحها يدويًا: اللغة والإدخال ← لوحة المفاتيح", Toast.LENGTH_LONG).show()
}

/**
 * يعرض نافذة اختيار اللوحة. لو اللوحة غير مفعّلة ما رح تظهر بالنافذة أصلًا، فنودّي المستخدم
 * للتفعيل أولًا بدل ما يشوف قائمة ما فيها سيمو.
 */
private fun openKeyboardPicker(context: Context) {
    if (!KeyboardStatusHelper.isKeyboardEnabled(context)) {
        Toast.makeText(context, "فعّل «سيمو كيبورد» أولًا ثم اخترها", Toast.LENGTH_SHORT).show()
        openInputMethodSettings(context)
        return
    }
    val shown = runCatching {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }.isSuccess
    if (!shown) {
        Toast.makeText(context, "ما انفتحت نافذة الاختيار. اختر اللوحة من زر تبديل الكيبورد بشريط التنقل", Toast.LENGTH_LONG).show()
    }
}

// ============================ شاشة الإعدادات ============================

private val IconBlue = Color(0xFF4C7DFF)
private val IconGreen = Color(0xFF34C759)
private val IconOrange = Color(0xFFFF9500)
private val IconPurple = Color(0xFFAF52DE)
private val IconPink = Color(0xFFFF375F)
private val IconTeal = Color(0xFF30B0C7)
private val IconGray = Color(0xFF8E8E93)

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
    val player = remember { KeyClickPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

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
        // ---- الترويسة ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("سيمو كيبورد", color = SemoPalette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("الإعدادات", color = SemoPalette.TextHint, fontSize = 13.sp)
            }
        }

        // ---- بطاقة الحالة ----
        StatusCard(isEnabled, isSelected, onEnable, onPick)

        // ---- تجربة ----
        Section("جرّب اللوحة") {
            Box(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
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
        }

        // ---- ترتيب المفاتيح ----
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
            Divider()
            SwitchRow("أرقام عربية هندية (١٢٣)", R.drawable.ic_ios_shift, IconOrange, settings.arabicDigits) { scope.launch { repo.setArabicDigits(it) } }
            Divider()
            SwitchRow("صف الأرقام فوق الحروف", R.drawable.ic_ios_shift, IconPurple, settings.numberRow) { scope.launch { repo.setNumberRow(it) } }
        }

        // ---- المظهر ----
        Section("المظهر") {
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
            Block("وضع اليد الواحدة", R.drawable.ic_tool_onehand, IconOrange) {
                Segmented(
                    options = listOf(OneHandMode.OFF to "معطّل", OneHandMode.LEFT to "يسار", OneHandMode.RIGHT to "يمين"),
                    selected = settings.oneHand,
                    onSelect = { scope.launch { repo.setOneHand(it) } }
                )
            }
            Divider()
            SwitchRow("معاينة الحرف فوق المفتاح", R.drawable.ic_ios_emoji, IconBlue, settings.keyPreview) { scope.launch { repo.setKeyPreview(it) } }
            Divider()
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
            // رفع الدائرتين عن أسفل الشاشة
            var raise by remember(settings.bottomRaiseDp) { mutableFloatStateOf(settings.bottomRaiseDp) }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("رفع الدائرتين عن أسفل الشاشة", color = SemoPalette.TextPrimary, fontSize = 15.sp)
                    Text(
                        if (raise.roundToInt() == 0) "ملاصقتان" else "${raise.roundToInt()}",
                        color = SemoPalette.TextSecondary,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = raise,
                    onValueChange = { raise = it },
                    onValueChangeFinished = { scope.launch { repo.setBottomRaise(raise.roundToInt().toFloat()) } },
                    valueRange = 0f..40f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = SemoPalette.Accent,
                        activeTrackColor = SemoPalette.Accent,
                        inactiveTrackColor = SemoPalette.Stroke
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= 35) {
                Divider()
                // ضبط دقيق لارتفاع أيقونة الكرة الأرضية كي تتوازى مع أزرار شريط التنقل بجهازك
                var globeOffset by remember(settings.globeOffsetDp) { mutableFloatStateOf(settings.globeOffsetDp) }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ارتفاع زر الكرة الأرضية", color = SemoPalette.TextPrimary, fontSize = 15.sp)
                        Text(
                            if (globeOffset.roundToInt() == 0) "تلقائي" else "${globeOffset.roundToInt()}",
                            color = SemoPalette.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = globeOffset,
                        onValueChange = { globeOffset = it },
                        onValueChangeFinished = { scope.launch { repo.setGlobeOffset(globeOffset.roundToInt().toFloat()) } },
                        valueRange = -8f..8f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = SemoPalette.Accent,
                            activeTrackColor = SemoPalette.Accent,
                            inactiveTrackColor = SemoPalette.Stroke
                        )
                    )
                    Text(
                        "حرّكه لفوق أو لتحت حتى تصير الأيقونة بمحاذاة زر إخفاء الكيبورد تمامًا. الوضع التلقائي مضبوط على منتصف شريط التنقل.",
                        color = SemoPalette.TextHint,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // ---- الكتابة ----
        Section("الكتابة") {
            SwitchRow("اقتراحات الكلمات", R.drawable.ic_ios_emoji, IconBlue, settings.suggestionsEnabled) { scope.launch { repo.setSuggestionsEnabled(it) } }
            Divider()
            SwitchRow("حرف كبير تلقائي بأول الجملة", R.drawable.ic_ios_shift, IconGreen, settings.autoCapitalize) { scope.launch { repo.setAutoCapitalize(it) } }
            Divider()
            SwitchRow("مسافتان = نقطة ومسافة", R.drawable.ic_tool_cursor, IconOrange, settings.doubleSpacePeriod) { scope.launch { repo.setDoubleSpacePeriod(it) } }
            Divider()
            SwitchRow("الكتابة بالسحب على الحروف", R.drawable.ic_tool_cursor, IconPurple, settings.swipeTyping) { scope.launch { repo.setSwipeTyping(it) } }
            Divider()
            SwitchRow("تصحيح تلقائي (dont ← don't، الى ← إلى)", R.drawable.ic_ios_backspace, IconTeal, settings.autoCorrect) { scope.launch { repo.setAutoCorrect(it) } }
            Divider()
            SwitchRow("ناتج العمليات الحسابية (60*30+20)", R.drawable.ic_tool_settings, IconPink, settings.mathResults) { scope.launch { repo.setMathResults(it) } }
            Divider()
            DictionaryBlock()
        }

        // ---- الصوت والاهتزاز ----
        Section("الصوت والاهتزاز") {
            SwitchRow("صوت الضغط على المفاتيح", R.drawable.ic_ios_mic, IconPink, settings.soundEnabled) { scope.launch { repo.setSoundEnabled(it) } }
            Divider()
            // شريط مستوى صوت الضغط: نحرّكه محليًا ونحفظ عند رفع الإصبع، ونشغّل نقرة تجريبية بالمستوى الجديد
            var volume by remember(settings.soundVolume) { mutableFloatStateOf(settings.soundVolume) }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مستوى الصوت", color = SemoPalette.TextPrimary, fontSize = 15.sp)
                    Text("${(volume * 100).roundToInt()}%", color = SemoPalette.TextSecondary, fontSize = 14.sp)
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = {
                        scope.launch { repo.setSoundVolume(volume) }
                        player.play(KeyFeedback.STANDARD, volume)
                    },
                    enabled = settings.soundEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = SemoPalette.Accent,
                        activeTrackColor = SemoPalette.Accent,
                        inactiveTrackColor = SemoPalette.Stroke
                    )
                )
            }
            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton("حرف", Modifier.weight(1f)) { player.play(KeyFeedback.STANDARD, volume) }
                PillButton("حذف", Modifier.weight(1f)) { player.play(KeyFeedback.DELETE, volume) }
                PillButton("مسافة", Modifier.weight(1f)) { player.play(KeyFeedback.MODIFIER, volume) }
            }
            Divider()
            SwitchRow("الاهتزاز عند الضغط", R.drawable.ic_tool_onehand, IconOrange, settings.hapticEnabled) { scope.launch { repo.setHapticEnabled(it) } }
        }

        // ---- الحافظة والبيانات ----
        Section("الحافظة والبيانات") {
            SwitchRow("حفظ سجل الحافظة", R.drawable.ic_tool_clipboard, IconBlue, settings.clipboardEnabled) { scope.launch { repo.setClipboardEnabled(it) } }
            Divider()
            ActionRow("مسح سجل الحافظة (مع المثبّت)") { scope.launch { clipboardRepo.clearAll() } }
            Divider()
            ActionRow("مسح الكلمات المتعلَّمة") { scope.launch { learnedRepo.clear() } }
        }

        // ---- الخصوصية ----
        Section("الخصوصية والأذونات") {
            Text(
                "خدمة لوحة المفاتيح نفسها ما بتتصل بالإنترنت أبدًا. الاتصال الوحيد هو لما تضغط بنفسك «تنزيل القاموس» بإعدادات الكتابة (ملف كلمات من GitHub). سجل الحافظة (نصوص وصور) والكلمات المتعلَّمة بيتخزنوا على جهازك فقط، وما بيتسجلوا بحقول كلمات المرور. الأذونات: الاهتزاز عند الضغط، والإنترنت لزر التنزيل فقط، وكلاهما عادي ما بيحتاج موافقتك.",
                color = SemoPalette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(14.dp)
            )
        }

        Section {
            ActionRow("إعادة عرض دليل الإعداد", onClick = onReplay)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** بطاقة الحالة: خطوتان بعلامات صح واضحة، وتتحول لشريط أخضر مختصر لما يكتمل الإعداد */
@Composable
private fun StatusCard(isEnabled: Boolean, isSelected: Boolean, onEnable: () -> Unit, onPick: () -> Unit) {
    val ready = isEnabled && isSelected
    val shape = RoundedCornerShape(22.dp)
    val bg = if (ready)
        Brush.linearGradient(listOf(Color(0xFF123524), Color(0xFF0F2A1D)))
    else
        Brush.linearGradient(listOf(Color(0xFF1B2547), Color(0xFF14192E)))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, shape)
            .border(1.dp, if (ready) Color(0xFF1F5A3B) else Color(0xFF2B3768), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckDot(ready, 30)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (ready) "اللوحة جاهزة وشغّالة" else "لسا ما خلصنا الإعداد",
                    color = SemoPalette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    if (ready) "افتح أي حقل كتابة وبتلاقي سيمو كيبورد" else "خطوتين وبتصير جاهزة",
                    color = SemoPalette.TextSecondary, fontSize = 12.sp
                )
            }
        }
        if (!ready) {
            StepRow("١. تفعيل اللوحة من إعدادات النظام", isEnabled, "تفعيل", onEnable)
            StepRow("٢. اختيارها كلوحة نشطة", isSelected, "اختيار", onPick)
        }
    }
}

@Composable
private fun StepRow(title: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckDot(done, 24)
        Spacer(Modifier.width(10.dp))
        Text(title, color = if (done) SemoPalette.TextSecondary else SemoPalette.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!done) {
            Box(
                modifier = Modifier
                    .background(SemoPalette.Accent, RoundedCornerShape(50))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) { Text(action, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun CheckDot(ok: Boolean, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(if (ok) SemoPalette.Ok else Color.Transparent, CircleShape)
            .border(2.dp, if (ok) SemoPalette.Ok else SemoPalette.Stroke, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (ok) Text("✓", color = Color(0xFF06260F), fontSize = (sizeDp * 0.55f).sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- مكوّنات مشتركة ----------

@Composable
private fun Section(title: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                color = SemoPalette.TextHint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SemoPalette.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, SemoPalette.Stroke, RoundedCornerShape(20.dp))
        ) { content() }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(1.dp)
            .background(SemoPalette.Stroke.copy(alpha = 0.6f))
    )
}

@Composable
private fun IconBadge(iconRes: Int, tint: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(tint, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SwitchRow(title: String, iconRes: Int, tint: Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(iconRes, tint)
        Spacer(Modifier.width(12.dp))
        Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SemoPalette.Ok,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = SemoPalette.SurfaceHigh,
                uncheckedThumbColor = SemoPalette.TextHint,
                uncheckedBorderColor = SemoPalette.Stroke
            )
        )
    }
}

/** تنزيل القاموس الكبير (إنكليزي + عربي) ودمجه بالإكمال التلقائي والكتابة بالسحب */
@Composable
private fun DictionaryBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(DictionaryManager.hasDownloaded(context)) }
    var counts by remember {
        mutableStateOf(BigDictionary.size(KeyboardLanguage.ENGLISH) to BigDictionary.size(KeyboardLanguage.ARABIC))
    }
    LaunchedEffect(Unit) {
        if (downloaded && counts.first == 0 && counts.second == 0) {
            withContext(Dispatchers.IO) { DictionaryManager.loadAll(context) }
            counts = BigDictionary.size(KeyboardLanguage.ENGLISH) to BigDictionary.size(KeyboardLanguage.ARABIC)
        }
    }
    Block("قاموس الاقتراحات الكبير", R.drawable.ic_ios_emoji, IconBlue) {
        Text(
            "ينزّل قائمتين مرتبتين حسب الشيوع (50 ألف كلمة إنكليزية و50 ألف عربية، من FrequencyWords / OpenSubtitles، رخصة CC-BY-SA 4.0) ويدمجهما مع الاقتراحات والكتابة بالسحب. حجمهما نحو 1MB، ويتم التنزيل مرة واحدة بضغطتك فقط.",
            color = SemoPalette.TextHint,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        val summary = when {
            status.isNotEmpty() -> status
            downloaded -> "محمّل: ${counts.first} كلمة إنكليزية · ${counts.second} كلمة عربية"
            else -> "غير محمّل (تعمل القوائم المدمجة الصغيرة فقط)"
        }
        Text(summary, color = SemoPalette.TextSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SolidButton(
                text = if (downloaded) "تحديث القاموس" else "تنزيل القاموس",
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                scope.launch {
                    busy = true
                    status = "جاري الاتصال..."
                    val result = withContext(Dispatchers.IO) {
                        DictionaryManager.download(context) { message -> status = message }
                    }
                    result.onSuccess { (en, ar) ->
                        counts = en to ar
                        downloaded = true
                        status = "تم التنزيل: $en كلمة إنكليزية · $ar كلمة عربية"
                    }.onFailure {
                        status = "فشل التنزيل، تأكد من الاتصال بالإنترنت ثم أعد المحاولة"
                    }
                    busy = false
                }
            }
            if (downloaded) {
                SolidButton(text = "حذف", enabled = !busy, modifier = Modifier.weight(1f)) {
                    DictionaryManager.delete(context)
                    downloaded = false
                    counts = 0 to 0
                    status = "حُذف القاموس المنزّل"
                }
            }
        }
    }
}

@Composable
private fun SolidButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (enabled) SemoPalette.Accent else SemoPalette.SurfaceHigh, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else SemoPalette.TextHint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Block(title: String, iconRes: Int, tint: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconRes, tint)
            Spacer(Modifier.width(12.dp))
            Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        content()
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) { Text(title, color = SemoPalette.AccentText, fontSize = 15.sp) }
}

@Composable
private fun PillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(SemoPalette.SurfaceHigh, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text("🔊 $text", color = SemoPalette.TextPrimary, fontSize = 13.sp) }
}

/** مفتاح تبديل مقسّم (Segmented) بدل الشرائح: خيار واحد مظلّل وبعرض متساوي */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SemoPalette.Field, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (value, label) ->
            val on = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) SemoPalette.Accent else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) Color.White else SemoPalette.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
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
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
                                .size(width = 22.dp, height = 30.dp)
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
