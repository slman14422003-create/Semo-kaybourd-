package com.semo.keyboard.ui

import com.semo.keyboard.ui.L
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.semo.keyboard.data.ClipboardRepository
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.data.isUserStorageUnlocked
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.data.LearnedWordsRepository
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.data.ShortcutsRepository
import com.semo.keyboard.ui.settings.SettingsScreen
import com.semo.keyboard.ui.onboarding.OnboardingKeyboardStatus
import com.semo.keyboard.ui.onboarding.OnboardingScreen
import com.semo.keyboard.ui.onboarding.OnboardingStep
import com.semo.keyboard.ui.theme.SemoAppTheme
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
        com.semo.keyboard.ui.settings.SettingsDeepLink.page.value = intent?.getStringExtra(EXTRA_PAGE)
        settingsRepository = SettingsRepository(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) { runCatching { settingsRepository.syncAppIcon() } }
        lifecycleScope.launch(Dispatchers.IO) { runCatching { autoDownloadArabicDictionary() } }
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

    private fun autoDownloadArabicDictionary() {
        val app = applicationContext
        if (!app.isUserStorageUnlocked()) return
        val language = KeyboardLanguage.ARABIC
        if (DictionaryManager.hasWords(app, language)) return
        val prefs = app.getSharedPreferences("dict_auto", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_try", 0L) < RETRY_MS) return
        prefs.edit().putLong("last_try", now).apply()
        DictionaryManager.download(app, language) { }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.semo.keyboard.ui.settings.SettingsDeepLink.page.value = intent.getStringExtra(EXTRA_PAGE)
    }

    companion object {
        const val EXTRA_PAGE = "settings_page"
        const val PAGE_VOICE = "voice"
        private const val RETRY_MS = 6L * 60L * 60L * 1000L
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
                delay(1500)
            }
        }
    }

    val current = settings ?: return // بانتظار قراءة التفضيلات
    remember { com.semo.keyboard.ui.AppLang.load(context) }
    val english = remember(current.appLanguage) {
        com.semo.keyboard.ui.AppLang.resolve(current.appLanguage).also { com.semo.keyboard.ui.AppLang.english = it }
    }
    val showOnboarding = !current.onboardingCompleted || forceOnboarding
    CompositionLocalProvider(LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl) {
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
    Toast.makeText(context, L("ما قدرت أفتح الإعدادات. افتحها يدويًا: اللغة والإدخال ← لوحة المفاتيح"), Toast.LENGTH_LONG).show()
}

/**
 * يعرض نافذة اختيار اللوحة. لو اللوحة غير مفعّلة ما رح تظهر بالنافذة أصلًا، فنودّي المستخدم
 * للتفعيل أولًا بدل ما يشوف قائمة ما فيها سيمو.
 */
private fun openKeyboardPicker(context: Context) {
    if (!KeyboardStatusHelper.isKeyboardEnabled(context)) {
        Toast.makeText(context, L("فعّل «سيمو كيبورد» أولًا ثم اخترها"), Toast.LENGTH_SHORT).show()
        openInputMethodSettings(context)
        return
    }
    val shown = runCatching {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }.isSuccess
    if (!shown) {
        Toast.makeText(context, L("ما انفتحت نافذة الاختيار. اختر اللوحة من زر تبديل الكيبورد بشريط التنقل"), Toast.LENGTH_LONG).show()
    }
}
