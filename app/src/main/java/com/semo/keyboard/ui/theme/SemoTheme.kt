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

/** ألوان لوحة المفاتيح بأسلوب iOS الحديث (رمادي ناعم، مفاتيح الحروف أفتح من المفاتيح الخاصة بالوضع الداكن) */
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
    val chip: Color,
    val shiftActive: Color,
    val onShiftActive: Color,
    val bubble: Color
) {
    companion object {
        val Light = SemoKeyboardColors(
            background = Color(0xFFD1D3D9),
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
            bubble = Color(0xFFFFFFFF)
        )
        val Dark = SemoKeyboardColors(
            background = Color(0xFF1E1E21),
            key = Color(0xFF5B5B60),
            keyPressed = Color(0xFF3F3F44),
            keySpecial = Color(0xFF3F3F44),
            keyAccent = Color(0xFF0A84FF),
            text = Color(0xFFFFFFFF),
            textMuted = Color(0xFF8E8E96),
            textOnAccent = Color.White,
            keyShadow = Color(0xFF0A0A0B),
            chip = Color(0xFF5B5B60),
            shiftActive = Color(0xFFF2F2F7),
            onShiftActive = Color(0xFF000000),
            bubble = Color(0xFF6C6C72)
        )
    }
}

object SemoDimens {
    val keyHeight = 48.dp
    val keySpacing = 6.dp
    val rowSpacing = 11.dp
    val sidePadding = 3.dp
    val keyRadius = 9.dp
    val keyShape = RoundedCornerShape(keyRadius)
}
