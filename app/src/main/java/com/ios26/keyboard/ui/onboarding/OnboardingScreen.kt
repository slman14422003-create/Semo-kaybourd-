package com.ios26.keyboard.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** خطوات معالج الإعداد الأول بعد تثبيت التطبيق */
enum class OnboardingStep { WELCOME, ENABLE, SELECT, DONE }

data class OnboardingKeyboardStatus(
    val isEnabled: Boolean,
    val isSelected: Boolean
)

/**
 * معالج إعداد بأربع خطوات، مطابق لتجربة تفعيل أي لوحة مفاتيح بديلة على أندرويد:
 * ترحيب → تفعيل من إعدادات النظام → اختيارها كلوحة نشطة → تأكيد الانتهاء.
 * الحالة (مفعّلة/مختارة) تُمرَّر من الخارج ليعاد فحصها كل مرة يعود فيها المستخدم من الإعدادات.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    status: OnboardingKeyboardStatus,
    onOpenEnableSettings: () -> Unit,
    onOpenKeyboardPicker: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        StepIndicator(step)

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "onboarding_step"
            ) { currentStep ->
                when (currentStep) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.ENABLE -> EnableStep(isEnabled = status.isEnabled, onOpenEnableSettings = onOpenEnableSettings)
                    OnboardingStep.SELECT -> SelectStep(isSelected = status.isSelected, onOpenKeyboardPicker = onOpenKeyboardPicker)
                    OnboardingStep.DONE -> DoneStep()
                }
            }
        }

        BottomActions(
            step = step,
            canProceed = when (step) {
                OnboardingStep.ENABLE -> status.isEnabled
                OnboardingStep.SELECT -> status.isSelected
                else -> true
            },
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip,
            onFinish = onFinish
        )
    }
}

@Composable
private fun StepIndicator(step: OnboardingStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OnboardingStep.entries.forEach { s ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(8.dp)
                    .background(
                        color = if (s.ordinal <= step.ordinal) Color(0xFF0A84FF) else Color(0xFFD1D4DA),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⌨️", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "أهلًا بك في iOS 26 Keyboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "خطوتان بسيطتان بس، وبتقدر تكتب بتصميم يحاكي لوحة iOS مباشرة من أي تطبيق.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
    }
}

@Composable
private fun EnableStep(isEnabled: Boolean, onOpenEnableSettings: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("١", style = MaterialTheme.typography.displaySmall, color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("فعّل اللوحة من إعدادات النظام", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "رح تنفتح شاشة \"إدارة لوحات المفاتيح\" بنظام أندرويد؛ فعّل مفتاح iOS 26 Keyboard من فيها.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenEnableSettings, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("افتح إعدادات لوحات المفاتيح")
        }
        Spacer(Modifier.height(12.dp))
        StatusBadge(isDone = isEnabled, doneText = "تم التفعيل ✓", pendingText = "لسا ما تفعّلت")
    }
}

@Composable
private fun SelectStep(isSelected: Boolean, onOpenKeyboardPicker: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("٢", style = MaterialTheme.typography.displaySmall, color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("اختر iOS 26 Keyboard كلوحة نشطة", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "بعد التفعيل، اختارها من قائمة لوحات المفاتيح المتاحة لتصير هي اللي تظهر عند الكتابة.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenKeyboardPicker, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("اختر اللوحة الآن")
        }
        Spacer(Modifier.height(12.dp))
        StatusBadge(isDone = isSelected, doneText = "تم الاختيار ✓", pendingText = "لسا ما اخترتها")
    }
}

@Composable
private fun DoneStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("خلصنا!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "افتح أي حقل كتابة بأي تطبيق وجرّب اللوحة. فيك ترجع لهاي الشاشة بأي وقت من الإعدادات.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
    }
}

@Composable
private fun StatusBadge(isDone: Boolean, doneText: String, pendingText: String) {
    Text(
        text = if (isDone) doneText else pendingText,
        color = if (isDone) Color(0xFF34C759) else Color(0xFFFF9500),
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun BottomActions(
    step: OnboardingStep,
    canProceed: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step != OnboardingStep.WELCOME) {
                TextButton(onClick = onBack) { Text("رجوع") }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (step == OnboardingStep.DONE) {
                Button(onClick = onFinish) { Text("ابدأ الكتابة") }
            } else {
                Row {
                    TextButton(onClick = onSkip) { Text("تخطّي", color = Color.Gray) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                    ) { Text("التالي") }
                }
            }
        }
    }
}
