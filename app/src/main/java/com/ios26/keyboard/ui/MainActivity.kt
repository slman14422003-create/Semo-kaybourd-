package com.ios26.keyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ios26.keyboard.data.SettingsRepository
import com.ios26.keyboard.ui.onboarding.OnboardingKeyboardStatus
import com.ios26.keyboard.ui.onboarding.OnboardingScreen
import com.ios26.keyboard.ui.onboarding.OnboardingStep
import com.ios26.keyboard.util.KeyboardStatusHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNavigation(settingsRepository = settingsRepository)
                }
            }
        }
    }
}

/**
 * يقرر أي شاشة تُعرض أولًا: معالج الإعداد إن لم يُنهه المستخدم بعد، وإلا شاشة الإعدادات مباشرة.
 * تفعيل/اختيار اللوحة يُعاد فحصهما تلقائيًا كل مرة يرجع فيها المستخدم للتطبيق (onResume).
 */
@Composable
private fun RootNavigation(settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onboardingCompleted by settingsRepository.isOnboardingCompleted.collectAsState(initial = null)

    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted != null && showOnboarding == null) {
            showOnboarding = onboardingCompleted == false
        }
    }

    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var isEnabled by remember { mutableStateOf(KeyboardStatusHelper.isKeyboardEnabled(context)) }
    var isSelected by remember { mutableStateOf(KeyboardStatusHelper.isKeyboardSelected(context)) }

    // يعيد فحص حالة اللوحة كل مرة تعود فيها الشاشة لـ RESUMED (بعد رجوع المستخدم من إعدادات النظام)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = KeyboardStatusHelper.isKeyboardEnabled(context)
                isSelected = KeyboardStatusHelper.isKeyboardSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    when (showOnboarding) {
        null -> Unit // بانتظار قراءة التفضيل المحفوظ قبل تحديد الشاشة الأولى
        true -> OnboardingScreen(
            step = step,
            status = OnboardingKeyboardStatus(isEnabled = isEnabled, isSelected = isSelected),
            onOpenEnableSettings = { openInputMethodSettings(context) },
            onOpenKeyboardPicker = { openKeyboardPicker(context) },
            onNext = {
                step = OnboardingStep.entries.getOrElse(step.ordinal + 1) { OnboardingStep.DONE }
            },
            onBack = {
                step = OnboardingStep.entries.getOrElse(step.ordinal - 1) { OnboardingStep.WELCOME }
            },
            onSkip = { step = OnboardingStep.DONE },
            onFinish = {
                scope.launch { settingsRepository.setOnboardingCompleted(true) }
                showOnboarding = false
            }
        )
        false -> SettingsScreen(
            settingsRepository = settingsRepository,
            isKeyboardEnabled = isEnabled,
            isKeyboardSelected = isSelected,
            onOpenLanguageSettings = { openInputMethodSettings(context) },
            onPickKeyboard = { openKeyboardPicker(context) },
            onReplayOnboarding = {
                step = OnboardingStep.WELCOME
                showOnboarding = true
            }
        )
    }
}

/** يفتح شاشة إدارة لوحات المفاتيح بأمان؛ لو تعذّر (روم غير قياسي) ما بيكرش التطبيق */
private fun openInputMethodSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
}

/** يفتح منتقي لوحة المفاتيح بأمان */
private fun openKeyboardPicker(context: Context) {
    runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }
}

@Composable
private fun SettingsScreen(
    settingsRepository: SettingsRepository,
    isKeyboardEnabled: Boolean,
    isKeyboardSelected: Boolean,
    onOpenLanguageSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isDark by settingsRepository.isDarkTheme.collectAsState(initial = false)
    val isSound by settingsRepository.isSoundEnabled.collectAsState(initial = true)
    val isHaptic by settingsRepository.isHapticEnabled.collectAsState(initial = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = "iOS 26 Keyboard", style = MaterialTheme.typography.headlineMedium)

        val statusText = when {
            isKeyboardEnabled && isKeyboardSelected -> "اللوحة مفعّلة ومختارة ✓"
            isKeyboardEnabled -> "مفعّلة، بس لسا ما اخترتها كلوحة نشطة"
            else -> "لسا ما فعّلت اللوحة"
        }
        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

        Button(onClick = onOpenLanguageSettings, modifier = Modifier.fillMaxWidth()) {
            Text("فعّل اللوحة من إعدادات النظام")
        }

        Button(onClick = onPickKeyboard, modifier = Modifier.fillMaxWidth()) {
            Text("اختر iOS 26 Keyboard كلوحة نشطة")
        }

        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text("إعادة عرض دليل الإعداد")
        }

        Divider()

        SettingRow("الوضع الداكن", isDark) { value ->
            scope.launch { settingsRepository.setDarkTheme(value) }
        }
        SettingRow("صوت الضغط على المفاتيح", isSound) { value ->
            scope.launch { settingsRepository.setSoundEnabled(value) }
        }
        SettingRow("الاهتزاز عند الضغط", isHaptic) { value ->
            scope.launch { settingsRepository.setHapticEnabled(value) }
        }
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
