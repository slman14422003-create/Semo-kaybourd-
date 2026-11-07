package com.semo.keyboard.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.semo.keyboard.R
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.domain.model.AutoCorrectLevel
import com.semo.keyboard.domain.model.BlurHighlight
import com.semo.keyboard.domain.model.BlurPreset
import com.semo.keyboard.domain.model.BlurSource
import com.semo.keyboard.domain.model.BlurTintMode
import com.semo.keyboard.domain.model.LearnSpeed
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.L
import com.semo.keyboard.ui.theme.FrostSpec
import com.semo.keyboard.ui.theme.drawFrostKey
import com.semo.keyboard.ui.theme.frostPanel
import com.semo.keyboard.ui.theme.semoColors
import com.semo.keyboard.ui.theme.withBlur
import com.semo.keyboard.util.BlurSupport
import com.semo.keyboard.util.FrostImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun AutoCorrectPage(
    repo: SettingsRepository,
    settings: SemoSettings,
    onBack: () -> Unit,
    onOpenDictionary: () -> Unit
) {
    val scope = rememberCoroutineScope()
    SubPage(L("التصحيح التلقائي والتعلّم"), onBack) {
        Section(L("التصحيح التلقائي")) {
            SwitchRow(
                L("التصحيح التلقائي"), R.drawable.ic_set_spell, IconTeal, settings.autoCorrect,
                subtitle = L("يستبدل الأخطاء الشائعة والواضحة عند الانتهاء من الكلمة")
            ) { scope.launch { repo.setAutoCorrect(it) } }
            Divider()
            Block(L("القوة"), R.drawable.ic_set_spell, IconTeal) {
                Segmented(
                    options = listOf(
                        AutoCorrectLevel.LIGHT to L("خفيف"),
                        AutoCorrectLevel.BALANCED to L("متوازن"),
                        AutoCorrectLevel.STRONG to L("قوي")
                    ),
                    selected = settings.autoCorrectLevel,
                    onSelect = { scope.launch { repo.setAutoCorrectLevel(it) } }
                )
            }
            Hint(
                when (settings.autoCorrectLevel) {
                    AutoCorrectLevel.LIGHT -> L("الاختصارات الثابتة وما علّمته للكيبورد فقط. بدون تخمين إملائي")
                    AutoCorrectLevel.BALANCED -> L("الاختصارات وما علّمته وأخطاء الحرف الواحد الواضحة في الكلمات الشائعة")
                    AutoCorrectLevel.STRONG -> L("يصحّح أيضًا الكلمات الأقل شيوعًا والأخطاء الأكبر قليلًا. استخدمه بحذر")
                }
            )
            Divider()
            SwitchRow(
                L("تصحيح الكلمات العربية"), R.drawable.ic_set_text, IconBlue, settings.autoCorrectArabic
            ) { scope.launch { repo.setAutoCorrectArabic(it) } }
            Divider()
            SwitchRow(
                L("تصحيح الكلمات بالحروف اللاتينية"), R.drawable.ic_set_text, IconBlue, settings.autoCorrectLatin
            ) { scope.launch { repo.setAutoCorrectLatin(it) } }
            Divider()
            var minDraft by remember(settings.autoCorrectMinLength) { mutableStateOf(settings.autoCorrectMinLength.toFloat()) }
            SliderRow(
                title = L("أقل طول للكلمة لتخمين الإملاء"),
                valueLabel = "${minDraft.roundToInt()}",
                value = minDraft,
                range = 2f..6f,
                steps = 3,
                onChange = { minDraft = it },
                onFinish = { scope.launch { repo.setAutoCorrectMinLength(minDraft.roundToInt()) } }
            )
            Divider()
            SwitchRow(
                L("التصحيح عند كتابة علامات الترقيم"), R.drawable.ic_set_text, IconPurple, settings.correctOnPunctuation,
                subtitle = L("يطبّق التصحيح أيضًا عند كتابة نقطة أو فاصلة أو علامة مشابهة")
            ) { scope.launch { repo.setCorrectOnPunctuation(it) } }
        }
        Section(L("الحماية")) {
            SwitchRow(
                L("عدم استبدال الكلمات المحفوظة"), R.drawable.ic_set_lock, IconGreen, settings.protectLearnedWords,
                subtitle = L("الكلمات التي كتبتها وأبقيتها لا يغيّرها التصحيح ولا الإكمال التلقائي")
            ) { scope.launch { repo.setProtectLearned(it) } }
            Divider()
            SwitchRow(
                L("عدم تغيير الأسماء"), R.drawable.ic_set_lock, IconGreen, settings.protectNames,
                subtitle = L("الكلمات التي تبدأ بحرف كبير في منتصف الجملة تبقى كما كتبتها")
            ) { scope.launch { repo.setProtectNames(it) } }
            Hint(L("نصيحة: اضغط حذف بعد التصحيح مباشرة لاسترجاع كلمتك ولن تُصحَّح مرة أخرى"))
        }
        Section(L("التعلّم")) {
            SwitchRow(
                L("تعلّم كلمات جديدة"), R.drawable.ic_set_dictionary, IconOrange, settings.learnWordsEnabled,
                subtitle = L("يتذكر الكلمات التي تكتبها فتُقترح ولا تُصحَّح أبدًا")
            ) { scope.launch { repo.setLearnWords(it) } }
            Divider()
            Block(L("سرعة التعلّم"), R.drawable.ic_set_dictionary, IconOrange) {
                Segmented(
                    options = listOf(
                        LearnSpeed.CAREFUL to L("حذر"),
                        LearnSpeed.NORMAL to L("عادي"),
                        LearnSpeed.FAST to L("سريع")
                    ),
                    selected = settings.learnSpeed,
                    onSelect = { scope.launch { repo.setLearnSpeed(it) } }
                )
            }
            Hint(
                when (settings.learnSpeed) {
                    LearnSpeed.CAREFUL -> L("تُحفظ الكلمة الجديدة بعد ما تكتبها مرتين")
                    LearnSpeed.NORMAL -> L("الكلمات الجديدة الواضحة تُحفظ فورًا، والأخطاء المحتملة بعد مرتين")
                    LearnSpeed.FAST -> L("كل كلمة جديدة تُحفظ فورًا")
                }
            )
            Divider()
            SwitchRow(
                L("تعلّم أزواج الكلمات"), R.drawable.ic_set_text, IconBlue, settings.learnNextWords,
                subtitle = L("يحسّن اقتراحات الكلمة التالية من كتابتك")
            ) { scope.launch { repo.setLearnNextWords(it) } }
            Divider()
            ActionRow(L("إدارة الكلمات المحفوظة"), onClick = onOpenDictionary)
        }
    }
}

@Composable
internal fun BlurPage(repo: SettingsRepository, settings: SemoSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { BlurSupport.attach(context.applicationContext) }
    val supported = BlurSupport.enabled
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { FrostImage.importFrom(context, uri) }
                if (ok) {
                    repo.setBlurImageStamp(System.currentTimeMillis())
                    repo.setBlurSource(BlurSource.IMAGE)
                    repo.setBlurEnabled(true)
                } else {
                    Toast.makeText(context, L("تعذّر فتح الصورة"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    SubPage(L("التمويه والزجاج"), onBack) {
        Section(null) {
            Box(Modifier.padding(10.dp)) { BlurPreview(settings) }
        }
        Section(L("التمويه")) {
            SwitchRow(
                L("تمويه الخلفية"), R.drawable.ic_set_theme, IconBlue, settings.blurEnabled,
                subtitle = L("يموّه التطبيق خلف الكيبورد مثل الزجاج المصنفر")
            ) { scope.launch { repo.setBlurEnabled(it) } }
            Hint(
                when {
                    !BlurSupport.available() -> L("التمويه الحقيقي يحتاج أندرويد 12 أو أحدث. سيُستخدم لوح ملوّن بدلًا منه")
                    supported -> L("تمويه النافذة متاح على هذا الجهاز")
                    else -> L("تمويه النافذة مُعطَّل من النظام (توفير البطارية أو خيارات المطوّر). سيُستخدم لوح ملوّن بدلًا منه")
                }
            )
            Divider()
            Block(L("النمط"), R.drawable.ic_set_theme, IconBlue) {
                Segmented(
                    options = listOf(
                        BlurPreset.CLEAR to L("شفاف"),
                        BlurPreset.THIN to L("رقيق"),
                        BlurPreset.REGULAR to L("عادي"),
                        BlurPreset.THICK to L("سميك")
                    ),
                    selected = settings.blurPreset,
                    onSelect = { scope.launch { repo.applyBlurPreset(it) } }
                )
            }
        }
        Section(L("مصدر الخلفية")) {
            Block(L("ماذا يُموَّه خلف المفاتيح؟"), R.drawable.ic_set_theme, IconBlue) {
                Segmented(
                    options = listOf(
                        BlurSource.WINDOW to L("التطبيق خلف الكيبورد"),
                        BlurSource.IMAGE to L("صورة مخصصة")
                    ),
                    selected = settings.blurSource,
                    onSelect = { scope.launch { repo.setBlurSource(it) } }
                )
            }
            if (settings.blurSource == BlurSource.IMAGE) {
                Divider()
                ActionRow(if (settings.blurImageStamp == 0L) L("اختيار صورة من المعرض") else L("تغيير الصورة")) {
                    picker.launch(arrayOf("image/*"))
                }
                if (settings.blurImageStamp != 0L) {
                    Divider()
                    ActionRow(L("حذف الصورة"), color = DangerRed) {
                        scope.launch {
                            withContext(Dispatchers.IO) { FrostImage.clear(context) }
                            repo.setBlurImageStamp(0L)
                            repo.setBlurSource(BlurSource.WINDOW)
                        }
                    }
                    Divider()
                    var imgRadiusDraft by remember(settings.blurImageRadius) { mutableStateOf(settings.blurImageRadius.toFloat()) }
                    SliderRow(
                        title = L("تمويه الصورة"),
                        valueLabel = "${imgRadiusDraft.roundToInt()}",
                        value = imgRadiusDraft,
                        range = 0f..50f,
                        steps = 24,
                        onChange = { imgRadiusDraft = it },
                        onFinish = { scope.launch { repo.setBlurImageRadius(imgRadiusDraft.roundToInt()) } }
                    )
                    var imgDimDraft by remember(settings.blurImageDim) { mutableStateOf(settings.blurImageDim) }
                    SliderRow(
                        title = L("تعتيم الصورة"),
                        valueLabel = "${(imgDimDraft * 100).roundToInt()}%",
                        value = imgDimDraft,
                        range = 0f..0.7f,
                        steps = 13,
                        onChange = { imgDimDraft = it },
                        onFinish = { scope.launch { repo.setBlurImageDim(imgDimDraft) } }
                    )
                    var imgSatDraft by remember(settings.blurImageSaturation) { mutableStateOf(settings.blurImageSaturation) }
                    SliderRow(
                        title = L("تشبّع الألوان"),
                        valueLabel = "${(imgSatDraft * 100).roundToInt()}%",
                        value = imgSatDraft,
                        range = 0.5f..2f,
                        steps = 14,
                        onChange = { imgSatDraft = it },
                        onFinish = { scope.launch { repo.setBlurImageSaturation(imgSatDraft) } }
                    )
                }
            }
            Hint(
                if (settings.blurSource == BlurSource.IMAGE)
                    L("الصورة تُموَّه داخل التطبيق، فتعمل على كل إصدارات أندرويد حتى بدون تمويه النظام")
                else
                    L("تمويه النافذة الحقيقي: يموّه ما خلف الكيبورد ويحتاج أندرويد 12 أو أحدث")
            )
        }
        Section(L("ضبط دقيق")) {
            var radiusDraft by remember(settings.blurRadius) { mutableStateOf(settings.blurRadius.toFloat()) }
            SliderRow(
                title = L("قوة التمويه"),
                valueLabel = "${radiusDraft.roundToInt()}",
                value = radiusDraft,
                range = 0f..150f,
                steps = 29,
                enabled = settings.blurEnabled,
                onChange = { radiusDraft = it },
                onFinish = { scope.launch { repo.setBlurRadius(radiusDraft.roundToInt()) } }
            )
            var tintDraft by remember(settings.blurTint) { mutableStateOf(settings.blurTint) }
            SliderRow(
                title = L("قوة الصبغة"),
                valueLabel = "${(tintDraft * 100).roundToInt()}%",
                value = tintDraft,
                range = 0f..1f,
                steps = 19,
                enabled = settings.blurEnabled,
                onChange = { tintDraft = it },
                onFinish = { scope.launch { repo.setBlurTint(tintDraft) } }
            )
            var keyDraft by remember(settings.glassOpacity) { mutableStateOf(settings.glassOpacity) }
            SliderRow(
                title = L("شفافية المفاتيح"),
                valueLabel = "${(keyDraft * 100).roundToInt()}%",
                value = keyDraft,
                range = 0.35f..1f,
                steps = 12,
                onChange = { keyDraft = it },
                onFinish = { scope.launch { repo.setGlassOpacity(keyDraft) } }
            )
            var sheenDraft by remember(settings.blurSheen) { mutableStateOf(settings.blurSheen) }
            SliderRow(
                title = L("لمعة الأعلى"),
                valueLabel = "${(sheenDraft * 100).roundToInt()}%",
                value = sheenDraft,
                range = 0f..1f,
                steps = 19,
                enabled = settings.blurEnabled,
                onChange = { sheenDraft = it },
                onFinish = { scope.launch { repo.setBlurSheen(sheenDraft) } }
            )
            var borderDraft by remember(settings.blurBorder) { mutableStateOf(settings.blurBorder) }
            SliderRow(
                title = L("لمعة الحواف"),
                valueLabel = "${(borderDraft * 100).roundToInt()}%",
                value = borderDraft,
                range = 0f..1f,
                steps = 19,
                enabled = settings.blurEnabled,
                onChange = { borderDraft = it },
                onFinish = { scope.launch { repo.setBlurBorder(borderDraft) } }
            )
            var edgeWidthDraft by remember(settings.blurEdgeWidth) { mutableStateOf(settings.blurEdgeWidth) }
            SliderRow(
                title = L("سماكة الحواف"),
                valueLabel = "${"%.1f".format(edgeWidthDraft)}",
                value = edgeWidthDraft,
                range = 0.5f..3f,
                steps = 4,
                enabled = settings.blurEnabled,
                onChange = { edgeWidthDraft = it },
                onFinish = { scope.launch { repo.setBlurEdgeWidth(edgeWidthDraft) } }
            )
            Divider()
            Block(L("لون اللمعة والحواف"), R.drawable.ic_set_theme, IconTeal) {
                Segmented(
                    options = listOf(
                        BlurHighlight.WHITE to L("أبيض"),
                        BlurHighlight.ACCENT to L("لون التمييز"),
                        BlurHighlight.AUTO to L("تلقائي")
                    ),
                    selected = settings.blurHighlight,
                    onSelect = { scope.launch { repo.setBlurHighlight(it) } }
                )
            }
            Divider()
            Block(L("لون الصبغة"), R.drawable.ic_set_theme, IconPurple) {
                Segmented(
                    options = listOf(
                        BlurTintMode.AUTO to L("السمة"),
                        BlurTintMode.ACCENT to L("لون التمييز"),
                        BlurTintMode.LIGHTEN to L("أفتح"),
                        BlurTintMode.DARKEN to L("أغمق")
                    ),
                    selected = settings.blurTintMode,
                    onSelect = { scope.launch { repo.setBlurTintMode(it) } }
                )
            }
        }
        Section(L("خيارات")) {
            SwitchRow(
                L("مفاتيح مصنفرة"), R.drawable.ic_set_theme, IconTeal, settings.blurKeys,
                subtitle = L("يضيف حافة زجاجية ولمعة على كل مفتاح")
            ) { scope.launch { repo.setBlurKeys(it) } }
            Divider()
            SwitchRow(
                L("لوح معتم بديل"), R.drawable.ic_set_lock, IconGray, settings.blurFallbackSolid,
                subtitle = L("عند عدم توفر التمويه، استخدم لوحًا شبه معتم ليبقى النص مقروءًا")
            ) { scope.launch { repo.setBlurFallbackSolid(it) } }
            Divider()
            ActionRow(L("إعادة ضبط التمويه")) {
                scope.launch {
                    repo.applyBlurPreset(BlurPreset.REGULAR)
                    repo.setBlurEnabled(false)
                    repo.setBlurTintMode(BlurTintMode.AUTO)
                    repo.setBlurKeys(true)
                    repo.setBlurFallbackSolid(true)
                    repo.setGlassOpacity(1f)
                    repo.setPanelOpacity(1f)
                    repo.setBlurSource(BlurSource.WINDOW)
                    repo.setBlurImageRadius(20)
                    repo.setBlurImageDim(0.1f)
                    repo.setBlurImageSaturation(1.2f)
                    repo.setBlurHighlight(BlurHighlight.WHITE)
                    repo.setBlurEdgeWidth(1f)
                }
            }
        }
    }
}

@Composable
private fun BlurPreview(settings: SemoSettings) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = remember(settings.style, dark, settings.keyboardTheme) { semoColors(settings.style, dark, settings.keyboardTheme) }
    val active = settings.blurEnabled
    val colors = remember(base, active, settings.glassOpacity, settings.blurTint, settings.blurTintMode, dark) {
        if (active) base.withBlur(settings.glassOpacity, settings.blurTint, settings.blurTintMode, dark) else base
    }
    val context = LocalContext.current
    val useImage = active && settings.blurSource == BlurSource.IMAGE && settings.blurImageStamp != 0L
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, useImage, settings.blurImageStamp, settings.blurImageRadius, settings.blurImageSaturation
    ) {
        value = if (useImage) {
            withContext(Dispatchers.Default) {
                FrostImage.render(context.applicationContext, settings.blurImageStamp, settings.blurImageRadius, settings.blurImageSaturation)
            }
        } else null
    }
    val highlight = when (settings.blurHighlight) {
        BlurHighlight.WHITE -> Color.White
        BlurHighlight.ACCENT -> colors.keyAccent
        BlurHighlight.AUTO -> if (dark) Color.White else Color(0xFF5B6B85)
    }
    val frost = remember(active, settings.blurKeys, settings.blurBorder, settings.blurSheen, highlight, settings.blurEdgeWidth, image, settings.blurImageDim) {
        FrostSpec(active, settings.blurKeys, settings.blurBorder, settings.blurSheen, highlight, settings.blurEdgeWidth, image, settings.blurImageDim)
    }
    val blurDp = if (active) (settings.blurRadius / 4f).coerceAtLeast(0f).dp else 0.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurDp)
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF3A7BD5), Color(0xFFB06AB3), Color(0xFFFF9966))))
                    val r = size.minDimension / 3f
                    drawCircle(Color(0xFFFFE259), r * 0.8f, Offset(size.width * 0.2f, size.height * 0.35f))
                    drawCircle(Color(0xFF34C759), r * 0.7f, Offset(size.width * 0.55f, size.height * 0.55f))
                    drawCircle(Color(0xFFFF375F), r * 0.9f, Offset(size.width * 0.85f, size.height * 0.3f))
                    drawCircle(Color(0xFF30B0C7), r * 0.6f, Offset(size.width * 0.4f, size.height * 0.2f))
                }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(128.dp)
                .frostPanel(colors.panel, frost)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val counts = listOf(10, 9, 7)
            counts.forEach { n ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(n) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .drawBehind {
                                    val radius = 5.dp.toPx()
                                    drawRoundRect(colors.key, cornerRadius = CornerRadius(radius))
                                    drawFrostKey(frost, radius)
                                }
                        )
                    }
                }
            }
        }
    }
}
