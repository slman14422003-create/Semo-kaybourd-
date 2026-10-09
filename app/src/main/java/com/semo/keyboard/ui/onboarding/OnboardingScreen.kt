package com.semo.keyboard.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.ui.theme.SemoPalette

enum class OnboardingStep { WELCOME, ENABLE, SELECT, DONE }

data class OnboardingKeyboardStatus(val isEnabled: Boolean, val isSelected: Boolean)

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
            .background(SemoPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepDots(current = step.ordinal, total = OnboardingStep.entries.size)

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(28.dp))

            when (step) {
                OnboardingStep.WELCOME -> StepText(
                    "أهلًا فيك بسيمو كيبورد",
                    "لوحة مفاتيح عربي وإنكليزي بتصميم أنيق وسريعة. خلّينا نجهّزها بـ ٣ خطوات بسيطة."
                )
                OnboardingStep.ENABLE -> {
                    StepText("١. فعّل اللوحة", "افتح إعدادات لوحات المفاتيح وفعّل «Semo Keyboard» من القائمة.")
                    Spacer(Modifier.height(16.dp))
                    StatusPill(status.isEnabled, "مفعّلة", "لسا ما انفعلت")
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("فتح الإعدادات", onOpenEnableSettings)
                }
                OnboardingStep.SELECT -> {
                    StepText("٢. اخترها كلوحة نشطة", "اختار «Semo Keyboard» من القائمة اللي رح تظهر لتبدأ تكتب فيها.")
                    Spacer(Modifier.height(16.dp))
                    StatusPill(status.isSelected, "هي اللوحة النشطة", "لسا مش مختارة")
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("اختيار اللوحة", onOpenKeyboardPicker)
                }
                OnboardingStep.DONE -> StepText(
                    "جاهز! 🎉",
                    "افتح أي تطبيق فيه حقل كتابة وبتلاقي سيمو كيبورد جاهزة. وتقدر تغيّر الثيم والاهتزاز من إعدادات التطبيق."
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step == OnboardingStep.WELCOME) {
                TextButton(onClick = onSkip) { Text("تخطّي", color = SemoPalette.TextHint) }
            } else if (step != OnboardingStep.DONE) {
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, SemoPalette.Stroke)
                ) { Text("رجوع", color = SemoPalette.TextSecondary) }
            } else {
                Spacer(Modifier.size(1.dp))
            }

            if (step == OnboardingStep.DONE) {
                PrimaryButton("ابدأ", onFinish)
            } else {
                PrimaryButton("التالي", onNext)
            }
        }
    }
}

@Composable
private fun StepText(title: String, body: String) {
    Text(title, color = SemoPalette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(body, color = SemoPalette.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SemoPalette.Accent, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StatusPill(ok: Boolean, okText: String, notText: String) {
    val color = if (ok) SemoPalette.Ok else SemoPalette.Warn
    Row(
        modifier = Modifier
            .background(SemoPalette.Surface, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(if (ok) okText else notText, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .background(if (i == current) SemoPalette.Accent else SemoPalette.Stroke, RoundedCornerShape(50))
            )
        }
    }
}
