package com.semo.keyboard.ui.anim

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf

/**
 * مضاعِف سرعة أنيميشن اللوحة الحالي (من الإعدادات ← المظهر والحجم ← سرعة الحركة):
 * 1 = عادية، أصغر من 1 = أسرع، أكبر من 1 = أبطأ، 0 = إيقاف الحركة تمامًا (فورية).
 * يُوفَّر بأعلى KeyboardScreen وSettingsScreen كي تقرأه كل الحركات بالأسفل.
 */
val LocalAnimScale = compositionLocalOf { 1f }

/** مدة Tween بعد ضربها بمضاعِف السرعة؛ 0 يعني حركة فورية بلا مدة */
fun <T> scaledTween(
    scale: Float,
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = IosEaseInOut
): FiniteAnimationSpec<T> {
    if (scale <= 0.001f) return tween(durationMillis = 0, delayMillis = 0)
    val d = (durationMillis * scale).toInt().coerceAtLeast(1)
    val dl = (delayMillis * scale).toInt().coerceAtLeast(0)
    return tween(durationMillis = d, delayMillis = dl, easing = easing)
}

/**
 * زنبرك بعد تعديل صلابته بمضاعِف السرعة (صلابة أقل = حركة أبطأ)؛ 0 يرجع حركة فورية بلا زنبرك
 * محسوسة. مناسب لأي animateTo بزنبرك (langShift، الفقاعات، الأيقونات...).
 */
fun <T> scaledSpring(
    scale: Float,
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium
): FiniteAnimationSpec<T> {
    if (scale <= 0.001f) return tween(durationMillis = 0)
    val adjusted = (stiffness / scale.coerceIn(0.2f, 3f)).coerceIn(40f, 4000f)
    return spring(dampingRatio = dampingRatio, stiffness = adjusted)
}

val IosEaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
val IosEaseOut: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
val IosPush: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

const val IosSmoothDamping = 1f
