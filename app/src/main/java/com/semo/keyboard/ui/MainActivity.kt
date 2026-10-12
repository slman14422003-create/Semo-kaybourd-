package com.semo.keyboard.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.semo.keyboard.data.ShortcutsRepository
import com.semo.keyboard.ui.settings.SettingsScreen
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
    private lateinit var shortcutsRepository: ShortcutsRepository

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
        shortcutsRepository = ShortcutsRepository(applicationContext)

        setContent {
            SemoAppTheme {
                // نص التطبيق عربي، فنثبّت الاتجاه RTL حتى لو لغة الجهاز غير ذلك
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(settingsRepository, clipboardRepository, learnedWordsRepository, shortcutsRepository)
                }
            }
        }
    }
}

@Composable
private fun Root(
    repo: SettingsRepository,
    clipboardRepo: ClipboardRepository,
    learnedRepo: LearnedWordsRepository,
    shortcutsRepo: ShortcutsRepository
) {
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
                // استعلام مدير الإدخال (Binder) بخيط خلفي كي لا يقطّع الأنيميشن، ونكتب الحالة فقط لو تغيّرت
                val (en, sel) = withContext(Dispatchers.IO) {
                    KeyboardStatusHelper.isKeyboardEnabled(context) to KeyboardStatusHelper.isKeyboardSelected(context)
                }
                if (en != isEnabled) isEnabled = en
                if (sel != isSelected) isSelected = sel
                delay(900)
            }
        }
    }

    val current = settings ?: return // بانتظار قراءة التفضيلات
    val showOnboarding = !current.onboardingCompleted || forceOnboarding
    Crossfade(targetState = showOnboarding, animationSpec = tween(380, easing = com.semo.keyboard.ui.anim.IosEaseInOut), label = "root") { onboarding ->
    if (onboarding) {
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
            shortcutsRepo = shortcutsRepo,
            settings = current,
            isEnabled = isEnabled,
            isSelected = isSelected,
            onEnable = { openInputMethodSettings(context) },
            onPick = { openKeyboardPicker(context) },
            onReplay = { step = OnboardingStep.WELCOME; forceOnboarding = true }
        )
    }
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
