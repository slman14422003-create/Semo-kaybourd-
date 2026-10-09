package com.semo.keyboard.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.ui.theme.SemoPalette
import kotlinx.coroutines.delay

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
    // ننتقل تلقائيًا للخطوة التالية أول ما تكتمل الخطوة الحالية (فقط لو كانت غير مكتملة عند دخولها،
    // كي لا نقفز للأمام لما يرجع المستخدم للخلف عمدًا)
    val enabledAtEntry = remember(step) { status.isEnabled }
    val selectedAtEntry = remember(step) { status.isSelected }
    LaunchedEffect(step, status.isEnabled, status.isSelected) {
        val advance = (step == OnboardingStep.ENABLE && status.isEnabled && !enabledAtEntry) ||
            (step == OnboardingStep.SELECT && status.isSelected && !selectedAtEntry)
        if (advance) {
            delay(700)
            onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SemoPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepDots(current = step.ordinal, total = OnboardingStep.entries.size)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // نرفع المحتوى قليلًا عن المنتصف تمامًا كي يبدو متوازنًا بصريًا
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(28.dp))

            when (step) {
                OnboardingStep.WELCOME -> StepText(
                    "أهلًا فيك بسيمو كيبورد",
                    "لوحة مفاتيح عربي وإنكليزي بتصميم أنيق وسريعة. خلّينا نجهّزها بخطوتين بسيطتين."
                )
                OnboardingStep.ENABLE -> {
                    StepText("١. فعّل اللوحة", "افتح إعدادات لوحات المفاتيح وفعّل «سيمو كيبورد» من القائمة، وبعدين ارجع هون.")
                    Spacer(Modifier.height(18.dp))
                    StatusPill(status.isEnabled, "مفعّلة ✓", "لسا ما انفعلت")
                    Spacer(Modifier.height(18.dp))
                    if (status.isEnabled) {
                        // لو كانت مفعّلة أصلًا عند دخول الخطوة ما في انتقال تلقائي (ممكن المستخدم رجع للخلف عمدًا)
                        Hint(if (enabledAtEntry) "اللوحة مفعّلة مسبقًا. اضغط «التالي» للمتابعة." else "تمام! جاري الانتقال للخطوة التالية...")
                    } else {
                        PrimaryButton("فتح الإعدادات", onOpenEnableSettings)
                        Spacer(Modifier.height(14.dp))
                        Hint("بعد التفعيل ارجع لهون وبتنتقل الخطوة تلقائيًا. لو انسألت عن «مخاطر لوحات المفاتيح» اضغط موافق، هيدا تنبيه عام من النظام.")
                    }
                }
                OnboardingStep.SELECT -> {
                    StepText("٢. اخترها كلوحة نشطة", "اختار «سيمو كيبورد» من القائمة اللي رح تظهر لتبدأ تكتب فيها.")
                    Spacer(Modifier.height(18.dp))
                    StatusPill(status.isSelected, "هي اللوحة النشطة ✓", "لسا مش مختارة")
                    Spacer(Modifier.height(18.dp))
                    if (status.isSelected) {
                        Hint(if (selectedAtEntry) "هي اللوحة النشطة مسبقًا. اضغط «التالي» للمتابعة." else "تمام! جاري الانتقال...")
                    } else {
                        PrimaryButton("اختيار اللوحة", onOpenKeyboardPicker)
                        Spacer(Modifier.height(14.dp))
                        Hint("لو اخترتها وبعدك شايف «لسا»، اضغط الحقل تحت واكتب فيه — بتنعرف تلقائيًا أول ما تظهر اللوحة.")
                        Spacer(Modifier.height(14.dp))
                        TestField()
                    }
                }
                OnboardingStep.DONE -> {
                    StepText(
                        "جاهز! 🎉",
                        "افتح أي تطبيق فيه حقل كتابة وبتلاقي سيمو كيبورد جاهزة. وتقدر تغيّر الشكل والترتيب والصوت من إعدادات التطبيق."
                    )
                    Spacer(Modifier.height(18.dp))
                    StatusPill(status.isEnabled, "مفعّلة ✓", "اللوحة غير مفعّلة بعد")
                    Spacer(Modifier.height(8.dp))
                    StatusPill(status.isSelected, "هي اللوحة النشطة ✓", "مش مختارة كلوحة نشطة بعد")
                    Spacer(Modifier.height(18.dp))
                    TestField()
                }
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
                val stepDone = when (step) {
                    OnboardingStep.ENABLE -> status.isEnabled
                    OnboardingStep.SELECT -> status.isSelected
                    else -> true
                }
                // لو الخطوة ما اكتملت نوضّح أن الضغط هون يتجاوزها (ممكن الفحص ما اشتغل على بعض الأجهزة)
                PrimaryButton(if (stepDone) "التالي" else "تخطّي الخطوة", onNext)
            }
        }
    }
}

/** حقل تجربة: أول ما يضغط فيه المستخدم تظهر اللوحة ويتأكد التطبيق أنها تعمل */
@Composable
private fun TestField() {
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = TextStyle(color = SemoPalette.TextPrimary, fontSize = 16.sp),
        cursorBrush = SolidColor(SemoPalette.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .background(SemoPalette.Field, shape)
            .border(1.dp, SemoPalette.Stroke, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text("جرّب الكتابة هون…", color = SemoPalette.TextHint, fontSize = 15.sp)
                inner()
            }
        }
    )
}

@Composable
private fun StepText(title: String, body: String) {
    Text(title, color = SemoPalette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(body, color = SemoPalette.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = SemoPalette.TextHint,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
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
