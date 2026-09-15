package com.ios26.keyboard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * لوحة ألوان وأبعاد مستوحاة من لغة تصميم iOS الحديثة (Liquid Glass):
 * سطوح شبه شفافة، ظلال ناعمة جدًا، وتباين هادئ بين الوضعين الفاتح والداكن.
 */
object IOSKeyboardColors {
    // Light mode
    val backgroundLight = Color(0xFFD1D4DA)
    val keyLight = Color(0xFFFFFFFF)
    val keySpecialLight = Color(0xFFAEB3BD)
    val keyAccentLight = Color(0xFF0A84FF)
    val textLight = Color(0xFF1C1C1E)

    // Dark mode
    val backgroundDark = Color(0xFF161617)
    val keyDark = Color(0xFF373739)
    val keySpecialDark = Color(0xFF242426)
    val keyAccentDark = Color(0xFF0A84FF)
    val textDark = Color(0xFFF2F2F7)

    fun background(isDark: Boolean) = if (isDark) backgroundDark else backgroundLight
    fun key(isDark: Boolean) = if (isDark) keyDark else keyLight
    fun keySpecial(isDark: Boolean) = if (isDark) keySpecialDark else keySpecialLight
    fun keyAccent(isDark: Boolean) = keyAccentLight.takeIf { !isDark } ?: keyAccentDark
    fun text(isDark: Boolean) = if (isDark) textDark else textLight
    fun textOnAccent() = Color.White
}

object IOSKeyboardShapes {
    val key = RoundedCornerShape(6.dp)
    val popup = RoundedCornerShape(10.dp)
}

object IOSKeyboardDimens {
    val keyHeight = 44.dp
    val keySpacing = 6.dp
    val rowSpacing = 10.dp
    val keyboardPadding = 4.dp
    val keyElevation = 1.dp
}
