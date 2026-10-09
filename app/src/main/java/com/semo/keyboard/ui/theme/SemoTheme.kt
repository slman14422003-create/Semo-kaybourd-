package com.semo.keyboard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

/** ألوان لوحة المفاتيح نفسها (فاتح / داكن) بنفس لغة الهوية */
data class SemoKeyboardColors(
    val background: Color,
    val key: Color,
    val keyPressed: Color,
    val keySpecial: Color,
    val keyAccent: Color,
    val text: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val keyShadow: Color,
    val chip: Color
) {
    companion object {
        val Light = SemoKeyboardColors(
            background = Color(0xFFD3D6DD),
            key = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFB4B9C4),
            keySpecial = Color(0xFFB4B9C4),
            keyAccent = SemoPalette.Accent,
            text = Color(0xFF1C1C1E),
            textMuted = Color(0xFF6B6F7A),
            textOnAccent = Color.White,
            keyShadow = Color(0xFF8E939E),
            chip = Color(0xFFFFFFFF)
        )
        val Dark = SemoKeyboardColors(
            background = Color(0xFF101013),
            key = Color(0xFF2C2C33),
            keyPressed = Color(0xFF45454F),
            keySpecial = Color(0xFF1D1D22),
            keyAccent = SemoPalette.Accent,
            text = Color(0xFFFFFFFF),
            textMuted = Color(0xFF8A8A96),
            textOnAccent = Color.White,
            keyShadow = Color(0xFF000000),
            chip = Color(0xFF2C2C33)
        )
    }
}

object SemoDimens {
    val keyHeight = 44.dp
    val keySpacing = 6.dp
    val rowSpacing = 10.dp
    val sidePadding = 4.dp
    val keyShape = RoundedCornerShape(7.dp)
}
