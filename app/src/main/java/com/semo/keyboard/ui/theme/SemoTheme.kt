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

/** ألوان لوحة المفاتيح بنمطي iOS 26 وiOS 18، فاتح وداكن */
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

private val Ios26Dark = SemoKeyboardColors(
    panel = Color(0xFF18181A),
    key = Color(0xFF2C2C2F),
    keyPressed = Color(0xFF454549),
    keySpecial = Color(0xFF2C2C2F),
    keyAccent = Color(0xFF0A84FF),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9A9AA2),
    textOnAccent = Color.White,
    keyShadow = Color(0x66000000),
    chip = Color(0xFF2C2C2F),
    shiftActive = Color(0xFFF2F2F7),
    onShiftActive = Color(0xFF000000),
    bubble = Color(0xFF3A3A3D),
    divider = Color(0xFF3A3A3D),
    suggestion = Color(0xFFC7C7CC)
)

private val Ios26Light = SemoKeyboardColors(
    panel = Color(0xFFE3E4E9),
    key = Color(0xFFFFFFFF),
    keyPressed = Color(0xFFC9CBD2),
    keySpecial = Color(0xFFF7F7FA),
    keyAccent = Color(0xFF007AFF),
    text = Color(0xFF000000),
    textMuted = Color(0xFF6B6F7A),
    textOnAccent = Color.White,
    keyShadow = Color(0x33000000),
    chip = Color(0xFFFFFFFF),
    shiftActive = Color(0xFF1C1C1E),
    onShiftActive = Color(0xFFFFFFFF),
    bubble = Color(0xFFFFFFFF),
    divider = Color(0xFFC4C6CC),
    suggestion = Color(0xFF3A3A3C)
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
    KeyboardStyle.IOS26 -> if (dark) Ios26Dark else Ios26Light
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

fun keyMetrics(style: KeyboardStyle, size: KeyboardSize): KeyMetrics {
    // نسب آيفون: ارتفاع المفتاح ≈ 43، المسافة بين الصفوف ≈ 12، وبين المفاتيح ≈ 6
    val height = when (size) {
        KeyboardSize.SMALL -> 40.dp
        KeyboardSize.MEDIUM -> 43.dp
        KeyboardSize.LARGE -> 47.dp
    }
    return if (style == KeyboardStyle.IOS26) {
        KeyMetrics(height, 6.dp, 12.dp, 3.dp, 11.dp, 24.dp, 54.dp, 44.dp)
    } else {
        // هامش جانبي إجمالي 3dp (نصف المسافة بين المفاتيح) كما بالصورة المرجعية، وزوايا مفتاح صغيرة
        KeyMetrics(height, 6.dp, 12.dp, 3.dp, 5.5.dp, 0.dp, 54.dp, 44.dp)
    }
}
