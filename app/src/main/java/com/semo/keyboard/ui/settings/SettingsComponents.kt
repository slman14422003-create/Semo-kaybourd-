package com.semo.keyboard.ui.settings

import com.semo.keyboard.ui.L
import android.graphics.Color as AndroidColor
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.semo.keyboard.R
import com.semo.keyboard.data.SettingsRepository
import com.semo.keyboard.data.DictionaryManager
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.SemoSettings
import com.semo.keyboard.ui.theme.SemoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.ui.graphics.graphicsLayer

internal val IconBlue = Color(0xFF4C7DFF)
internal val IconGreen = Color(0xFF34C759)
internal val IconOrange = Color(0xFFFF9500)
internal val IconPurple = Color(0xFFAF52DE)
internal val IconPink = Color(0xFFFF375F)
internal val IconTeal = Color(0xFF30B0C7)
internal val IconGray = Color(0xFF8E8E93)
internal val DangerRed = Color(0xFFFF453A)

/** بطاقة الحالة: خطوتان بعلامات صح واضحة، وتتحول لشريط أخضر مختصر لما يكتمل الإعداد */
@Composable
internal fun StatusCard(isEnabled: Boolean, isSelected: Boolean, onEnable: () -> Unit, onPick: () -> Unit) {
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
                    if (ready) L("اللوحة جاهزة وشغّالة") else L("لسا ما خلصنا الإعداد"),
                    color = SemoPalette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    if (ready) L("افتح أي حقل كتابة وبتلاقي سيمو كيبورد") else L("خطوتين وبتصير جاهزة"),
                    color = SemoPalette.TextSecondary, fontSize = 12.sp
                )
            }
        }
        if (!ready) {
            StepRow(L("١. تفعيل اللوحة من إعدادات النظام"), isEnabled, L("تفعيل"), onEnable)
            StepRow(L("٢. اختيارها كلوحة نشطة"), isSelected, L("اختيار"), onPick)
        }
    }
}

@Composable
internal fun StepRow(title: String, done: Boolean, action: String, onClick: () -> Unit) {
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
internal fun CheckDot(ok: Boolean, sizeDp: Int) {
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
internal fun Section(title: String? = null, content: @Composable () -> Unit) {
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
internal fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(1.dp)
            .background(SemoPalette.Stroke.copy(alpha = 0.6f))
    )
}

@Composable
internal fun IconBadge(iconRes: Int, tint: Color) {
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
internal fun SwitchRow(
    title: String,
    iconRes: Int,
    tint: Color,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(iconRes, tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = SemoPalette.TextHint, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        com.semo.keyboard.ui.anim.IosSwitch(
            checked = checked,
            onCheckedChange = onChange,
        )
    }
}

/**
 * مكتبة اللغات: كل لغة تُفعَّل لتظهر بدورة الكرة الأرضية. التنزيل نفسه (قاموس ونموذج جمل) صار من صفحة «التنزيلات والتخزين».
 */
@Composable
internal fun LanguageLibraryBlock(repo: SettingsRepository, settings: SemoSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Section(L("مكتبة اللغات")) {
        Text(
            L("فعّل اللغات اللي بدك إياها بدورة زر الكرة الأرضية. تنزيل القاموس ونموذج الجمل لكل لغة من صفحة «التنزيلات والتخزين» بالإعدادات."),
            color = SemoPalette.TextHint,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(14.dp)
        )
        KeyboardLanguage.values().forEach { lang ->
            Divider()
            val on = lang in settings.enabledLanguages
            val hasWords = DictionaryManager.hasWords(context, lang)
            val hasModel = DictionaryManager.hasModel(context, lang)
            val state = when {
                hasWords && hasModel -> L("منزَّلة: قاموس + نموذج جمل")
                hasWords -> L("منزَّلة: قاموس فقط")
                lang.builtIn -> L("مدمجة (تنزيل القاموس الكامل اختياري)")
                else -> L("غير منزَّلة")
            }
            SwitchRow(lang.nativeName, R.drawable.ic_ios_globe, if (lang.rtl) IconGreen else IconBlue, on, subtitle = state) { v ->
                val list = if (v) settings.enabledLanguages + lang else settings.enabledLanguages - lang
                if (list.isNotEmpty()) {
                    scope.launch {
                        repo.setEnabledLanguages(list)
                        withContext(Dispatchers.IO) { if (v) DictionaryManager.load(context, lang) else DictionaryManager.unload(lang) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SolidButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun Block(title: String, iconRes: Int, tint: Color, content: @Composable () -> Unit) {
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
internal fun ActionRow(title: String, color: Color = SemoPalette.AccentText, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) { Text(title, color = color, fontSize = 15.sp) }
}

@Composable
internal fun PillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
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
internal fun LayoutPreview(rows: List<List<String>>) {
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


// ---------- مكوّنات صفحات الإعدادات الجديدة ----------

/** صف تنقّل لصفحة فرعية: أيقونة + عنوان + ملخص للحالة الحالية + سهم */
@Composable
internal fun NavRow(title: String, summary: String, iconRes: Int, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(iconRes, tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (summary.isNotEmpty()) {
                Text(summary, color = SemoPalette.TextHint, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
            }
        }
        // السهم يشير لليسار لأن الاتجاه RTL (الأيقونة الأصلية تشير لليمين)
        Icon(
            painter = painterResource(R.drawable.ic_tool_chevron),
            contentDescription = null,
            tint = SemoPalette.TextHint,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = -1f }
        )
    }
}

/** هيكل الصفحة الفرعية: شريط علوي برجوع وعنوان، ثم محتوى قابل للتمرير */
@Composable
internal fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SemoPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(SemoPalette.Surface, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                // بالـ RTL سهم الرجوع يشير لليمين = اتجاه الأيقونة الأصلي
                Icon(
                    painter = painterResource(R.drawable.ic_tool_chevron),
                    contentDescription = L("رجوع"),
                    tint = SemoPalette.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = SemoPalette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** شرح صغير تحت قسم */
@Composable
internal fun Hint(text: String) {
    Text(
        text,
        color = SemoPalette.TextHint,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

/** صف شريط تمرير بعنوان وقيمة ظاهرة: يحفظ عند رفع الإصبع فقط */
@Composable
internal fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp)
            Text(valueLabel, color = SemoPalette.TextSecondary, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinish,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SemoPalette.Accent,
                activeTrackColor = SemoPalette.Accent,
                inactiveTrackColor = SemoPalette.Stroke
            )
        )
    }
}

/** صف عنصر بشريط الأدوات: أيقونة + اسم + سهما ترتيب + مفتاح إظهار/إخفاء */
@Composable
internal fun ToolbarItemRow(
    title: String,
    iconRes: Int,
    tint: Color,
    checked: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(iconRes, tint)
        Spacer(Modifier.width(12.dp))
        Text(title, color = SemoPalette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        ToolbarArrow("▲", canUp, onUp)
        ToolbarArrow("▼", canDown, onDown)
        Spacer(Modifier.width(6.dp))
        com.semo.keyboard.ui.anim.IosSwitch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = canToggle,
        )
    }
}

@Composable
private fun ToolbarArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) SemoPalette.TextSecondary else SemoPalette.TextHint.copy(alpha = 0.35f),
            fontSize = 14.sp
        )
    }
}
