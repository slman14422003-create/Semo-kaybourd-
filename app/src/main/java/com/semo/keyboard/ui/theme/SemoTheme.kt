package com.semo.keyboard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.semo.keyboard.domain.model.KeyboardSize
import com.semo.keyboard.domain.model.KeyboardStyle

/** هوية سيمو البصرية: رمادي فحمي متدرّج + أزرق واضح (مأخوذة من تصميم GITHUB-MANGER) */
object SemoPalette {
    val Bg = Color(0xFF0B0B0D)
    val Surface = Color(0xFF1B1B1F)
    val SurfaceHigh = Color(0xFF26262B)
    val Field = Color(0xFF141417)
    val Stroke = Color(0xFF36363D)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB7B7C2)
    val TextHint = Color(0xFF8A8A96)
    val Accent = Color(0xFF4C7DFF)
    val AccentText = Color(0xFF9DB5FF)
    val AccentSoft = Color(0xFF16224A)
    val Ok = Color(0xFF4ADE80)
    val Bad = Color(0xFFFF6B6B)
    val Warn = Color(0xFFF5B94A)
}

@Composable
fun SemoAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SemoPalette.Accent,
            onPrimary = Color.White,
            background = SemoPalette.Bg,
            onBackground = SemoPalette.TextPrimary,
            surface = SemoPalette.Surface,
            onSurface = SemoPalette.TextPrimary,
            surfaceVariant = SemoPalette.SurfaceHigh,
            onSurfaceVariant = SemoPalette.TextSecondary,
            outline = SemoPalette.Stroke,
            secondaryContainer = SemoPalette.AccentSoft,
            onSecondaryContainer = SemoPalette.AccentText
        ),
        content = content
    )
}

/** ألوان لوحة المفاتيح بنمط iOS 18، فاتح وداكن */
data class SemoKeyboardColors(
    val panel: Color,
    val key: Color,
    val keyPressed: Color,
    val keySpecial: Color,
    val keyAccent: Color,
    val text: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val keyShadow: Color,
    val chip: Color,
    val shiftActive: Color,
    val onShiftActive: Color,
    val bubble: Color,
    val divider: Color,
    val suggestion: Color
)

/** ألوان مقيسة بالبكسل من صورة كيبورد آيفون (iOS 18 داكن) */
private val Ios18Dark = SemoKeyboardColors(
    panel = Color(0xFF222325),
    key = Color(0xFF646567),
    keyPressed = Color(0xFF7B7C7E),
    keySpecial = Color(0xFF3F4042),
    keyAccent = Color(0xFF0A84FF),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9B9C9F),
    textOnAccent = Color.White,
    keyShadow = Color(0xFF0C0C0D),
    chip = Color(0xFF646567),
    shiftActive = Color(0xFFD2D3D5),
    onShiftActive = Color(0xFF000000),
    bubble = Color(0xFF6F7072),
    divider = Color(0xFF48494B),
    suggestion = Color(0xFFE9EAEC)
)

private val Ios18Light = SemoKeyboardColors(
    panel = Color(0xFFD1D3D9),
    key = Color(0xFFFFFFFF),
    keyPressed = Color(0xFFADB1BB),
    keySpecial = Color(0xFFADB1BB),
    keyAccent = Color(0xFF007AFF),
    text = Color(0xFF000000),
    textMuted = Color(0xFF6B6F7A),
    textOnAccent = Color.White,
    keyShadow = Color(0xFF898C94),
    chip = Color(0xFFFFFFFF),
    shiftActive = Color(0xFFFFFFFF),
    onShiftActive = Color(0xFF000000),
    bubble = Color(0xFFFFFFFF),
    divider = Color(0xFFB4B7BF),
    suggestion = Color(0xFF1C1C1E)
)

fun semoColors(style: KeyboardStyle, dark: Boolean): SemoKeyboardColors = when (style) {
    KeyboardStyle.IOS18 -> if (dark) Ios18Dark else Ios18Light
}

/** قياسات اللوحة حسب النمط وارتفاع المفاتيح */
data class KeyMetrics(
    val keyHeight: Dp,
    val keySpacing: Dp,
    val rowSpacing: Dp,
    val sidePadding: Dp,
    val keyRadius: Dp,
    val panelRadius: Dp,
    val utilHeight: Dp,
    val stripHeight: Dp
) {
    val rowHeight: Dp get() = keyHeight + rowSpacing
    val keyShape: RoundedCornerShape get() = RoundedCornerShape(keyRadius)
}

/**
 * قياسات المفاتيح تُحسب من عرض الشاشة بنسب مقيسة بالبكسل من صورة مرجعية لكيبورد آيفون:
 * عرض المفتاح = 0.82 من خانة الحرف (الفراغ الأفقي 0.18)، ارتفاعه = 1.27 × عرضه،
 * والمسافة بين الصفوف = 0.41 × عرض المفتاح. [widthDp] عرض اللوحة الفعلي (أقل مع اليد الواحدة).
 */
fun keyMetrics(style: KeyboardStyle, size: KeyboardSize, widthDp: Float = 411f): KeyMetrics {
    val pitch = (widthDp - 6f) / 10f
    val keyWidth = pitch * 0.82f
    val factor = when (size) {
        KeyboardSize.SMALL -> 0.93f
        KeyboardSize.MEDIUM -> 1f
        KeyboardSize.LARGE -> 1.09f
    }
    val height = (keyWidth * 1.27f * factor).coerceIn(37f, 52f)
    val rowSpacing = (keyWidth * 0.41f).coerceIn(10f, 16f)
    val keySpacing = (pitch * 0.18f).coerceIn(5f, 9f)
    val radius = (keyWidth * 0.17f).coerceIn(5f, 8f)
    return KeyMetrics(height.dp, keySpacing.dp, rowSpacing.dp, 3.dp, radius.dp, 0.dp, 54.dp, 44.dp)
}
