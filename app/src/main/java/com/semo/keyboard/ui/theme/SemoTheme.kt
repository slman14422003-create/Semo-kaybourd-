package com.semo.keyboard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.semo.keyboard.domain.model.KeyboardSize
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.KeyboardTheme
import com.semo.keyboard.domain.model.KeySpacing

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
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.LocalIndication provides com.semo.keyboard.ui.anim.IosIndication,
                content = content
            )
        }
    )
}

/** ألوان لوحة المفاتيح بنمط iOS 18، فاتح وداكن */
@Immutable
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

/** ألوان iOS 18 الداكنة المقيسة من صورة مرجعية، مع تغميق خفيف بنسبة 8% */
private val Ios18Dark = SemoKeyboardColors(
    panel = Color(0xFF1F2022),
    key = Color(0xFF5C5D5F),
    keyPressed = Color(0xFF717274),
    keySpecial = Color(0xFF3A3B3D),
    keyAccent = Color(0xFF0A84FF),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9B9C9F),
    textOnAccent = Color.White,
    keyShadow = Color(0xFF0A0A0B),
    chip = Color(0xFF5C5D5F),
    shiftActive = Color(0xFFD2D3D5),
    onShiftActive = Color(0xFF000000),
    bubble = Color(0xFF666769),
    divider = Color(0xFF424345),
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

private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

private fun darkTheme(panel: Long, key: Long, special: Long, accent: Long, onAccent: Color = Color.White): SemoKeyboardColors {
    val p = Color(panel)
    val k = Color(key)
    val a = Color(accent)
    return SemoKeyboardColors(
        panel = p,
        key = k,
        keyPressed = mix(k, Color.White, 0.1f),
        keySpecial = Color(special),
        keyAccent = a,
        text = Color.White,
        textMuted = mix(Color(0xFF9B9C9F), a, 0.12f),
        textOnAccent = onAccent,
        keyShadow = mix(p, Color.Black, 0.6f),
        chip = k,
        shiftActive = Color(0xFFD2D3D5),
        onShiftActive = Color.Black,
        bubble = mix(k, Color.White, 0.05f),
        divider = mix(p, Color.White, 0.13f),
        suggestion = Color(0xFFE9EAEC)
    )
}

private fun lightTheme(panel: Long, special: Long, accent: Long, shadow: Long, onAccent: Color = Color.White): SemoKeyboardColors {
    val p = Color(panel)
    val s = Color(special)
    val a = Color(accent)
    return SemoKeyboardColors(
        panel = p,
        key = Color.White,
        keyPressed = s,
        keySpecial = s,
        keyAccent = a,
        text = Color.Black,
        textMuted = mix(Color(0xFF6B6F7A), a, 0.12f),
        textOnAccent = onAccent,
        keyShadow = Color(shadow),
        chip = Color.White,
        shiftActive = Color.White,
        onShiftActive = Color.Black,
        bubble = Color.White,
        divider = mix(p, Color.Black, 0.1f),
        suggestion = Color(0xFF1C1C1E)
    )
}

private val ThemeMidnightDark = darkTheme(0xFF000000, 0xFF2C2C2E, 0xFF1A1A1C, 0xFF0A84FF)
private val ThemeOceanDark = darkTheme(0xFF0F1B26, 0xFF2B4256, 0xFF1C2E3E, 0xFF32ADE6)
private val ThemeForestDark = darkTheme(0xFF121C16, 0xFF34503E, 0xFF1F3126, 0xFF30D158, Color.Black)
private val ThemeRoseDark = darkTheme(0xFF221619, 0xFF5A3B43, 0xFF3B262C, 0xFFFF375F)
private val ThemeSunsetDark = darkTheme(0xFF211812, 0xFF5A4332, 0xFF3A2A1D, 0xFFFF9F0A, Color.Black)
private val ThemeLavenderDark = darkTheme(0xFF1A1724, 0xFF463F63, 0xFF2D2842, 0xFFBF5AF2)
private val ThemeGraphiteDark = darkTheme(0xFF17181A, 0xFF3A3C40, 0xFF26282B, 0xFF8E8E93)
private val ThemeMintDark = darkTheme(0xFF0F1F1E, 0xFF2E5552, 0xFF1B3533, 0xFF63E6D4, Color.Black)

private val ThemeMidnightLight = lightTheme(0xFFE4E5E9, 0xFFB7BAC2, 0xFF007AFF, 0xFF8D909A)
private val ThemeOceanLight = lightTheme(0xFFCFE0EC, 0xFFA9C4D6, 0xFF0A84C8, 0xFF7E9BAE)
private val ThemeForestLight = lightTheme(0xFFCFE0D3, 0xFFA7C4AE, 0xFF248A3D, 0xFF7C9A84)
private val ThemeRoseLight = lightTheme(0xFFEBD3D8, 0xFFD3A9B2, 0xFFE0245E, 0xFFA8828A)
private val ThemeSunsetLight = lightTheme(0xFFEDDCC8, 0xFFD6B48F, 0xFFE07B00, 0xFFA88B6A)
private val ThemeLavenderLight = lightTheme(0xFFDDD6EC, 0xFFB9AED6, 0xFF8E44C9, 0xFF8F86A8)
private val ThemeGraphiteLight = lightTheme(0xFFD8D9DC, 0xFFB3B5BA, 0xFF636366, 0xFF8A8C91)
private val ThemeMintLight = lightTheme(0xFFD0E6E3, 0xFFA8CBC6, 0xFF00A896, 0xFF7FA09B)

fun semoColors(style: KeyboardStyle, dark: Boolean, theme: KeyboardTheme = KeyboardTheme.IOS): SemoKeyboardColors = when (style) {
    KeyboardStyle.IOS18 -> when (theme) {
        KeyboardTheme.IOS -> if (dark) Ios18Dark else Ios18Light
        KeyboardTheme.MIDNIGHT -> if (dark) ThemeMidnightDark else ThemeMidnightLight
        KeyboardTheme.OCEAN -> if (dark) ThemeOceanDark else ThemeOceanLight
        KeyboardTheme.FOREST -> if (dark) ThemeForestDark else ThemeForestLight
        KeyboardTheme.ROSE -> if (dark) ThemeRoseDark else ThemeRoseLight
        KeyboardTheme.SUNSET -> if (dark) ThemeSunsetDark else ThemeSunsetLight
        KeyboardTheme.LAVENDER -> if (dark) ThemeLavenderDark else ThemeLavenderLight
        KeyboardTheme.GRAPHITE -> if (dark) ThemeGraphiteDark else ThemeGraphiteLight
        KeyboardTheme.MINT -> if (dark) ThemeMintDark else ThemeMintLight
    }
}

/** قياسات اللوحة حسب النمط وارتفاع المفاتيح */
@Immutable
data class KeyMetrics(
    val keyHeight: Dp,
    val keySpacing: Dp,
    val rowSpacing: Dp,
    val sidePadding: Dp,
    val keyRadius: Dp,
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
fun keyMetrics(
    style: KeyboardStyle,
    size: KeyboardSize,
    widthDp: Float = 411f,
    spacing: KeySpacing = KeySpacing.NORMAL,
    scale: Float = 1f
): KeyMetrics {
    val sc = scale.coerceIn(0.8f, 1.3f)
    val pitch = (widthDp - 6f) / 10f
    val keyWidth = pitch * 0.82f
    val factor = when (size) {
        KeyboardSize.SMALL -> 0.93f
        KeyboardSize.MEDIUM -> 1f
        KeyboardSize.LARGE -> 1.09f
    }
    val height = (keyWidth * 1.27f * factor * sc).coerceIn(32f, 66f)
    // المسافة بين المفاتيح حسب اختيار المستخدم (ضيّق/عادي/واسع)
    val rowSpacing = ((keyWidth * 0.41f).coerceIn(10f, 16f) * spacing.vertical * (0.6f + 0.4f * sc)).coerceIn(6f, 21f)
    val keySpacing = ((pitch * 0.18f).coerceIn(5f, 9f) * spacing.horizontal).coerceIn(3.5f, 12f)
    val radius = (keyWidth * 0.2f).coerceIn(5.5f, 9f)
    return KeyMetrics(height.dp, keySpacing.dp, rowSpacing.dp, 3.dp, radius.dp, 54.dp, (46f * (0.85f + 0.15f * sc)).dp)
}

/** مقياس حجم اللوحة الحالي (0.8–1.3): يكبّر الخطوط والأيقونات مع ارتفاع المفاتيح */
val LocalKeyboardScale = androidx.compose.runtime.compositionLocalOf { 1f }
