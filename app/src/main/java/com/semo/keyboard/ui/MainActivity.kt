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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.semo.keyboard.data.SettingsRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // أندرويد 16: العرض من حافة لحافة إلزامي، فنتعامل مع الـ insets بأنفسنا
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            SemoAppTheme {
                // نص التطبيق عربي، فنثبّت الاتجاه RTL حتى لو لغة الجهاز غير ذلك
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(settingsRepository)
                }
            }
        }
    }
}

@Composable
private fun Root(repo: SettingsRepository) {
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
            Text("المظهر", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "حسب النظام",
                    ThemeMode.LIGHT to "فاتح",
                    ThemeMode.DARK to "داكن"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { repo.setThemeMode(mode) } },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SemoPalette.AccentSoft,
                            selectedLabelColor = SemoPalette.AccentText,
                            labelColor = SemoPalette.TextSecondary
                        )
                    )
                }
            }
            SwitchRow("صف الأرقام فوق الحروف", settings.numberRow) { scope.launch { repo.setNumberRow(it) } }
            SwitchRow("صوت الضغط على المفاتيح", settings.soundEnabled) { scope.launch { repo.setSoundEnabled(it) } }
            SwitchRow("الاهتزاز عند الضغط", settings.hapticEnabled) { scope.launch { repo.setHapticEnabled(it) } }
        }

        SemoCard {
            Text("الخصوصية والأذونات", color = SemoPalette.TextSecondary, fontSize = 13.sp)
            Text(
                "سيمو كيبورد ما بتتصل بالإنترنت وما بتجمع أي نص بتكتبه. الإذن الوحيد المستخدم هو الاهتزاز عند الضغط، وهو إذن عادي ما بيحتاج موافقتك.",
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
        Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SemoPalette.Accent, checkedThumbColor = Color.White)
        )
    }
}
