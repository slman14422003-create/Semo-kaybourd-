package com.semo.keyboard.domain.logic

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.RoundedCorner

object AutoFit {

    fun keyHeight(
        widthDp: Int,
        heightDp: Int,
        baseKeyHeight: Float,
        rowSpacing: Float,
        stripHeight: Float,
        utilHeight: Float,
        rows: Float
    ): Float {
        val landscape = widthDp > heightDp
        val shortest = minOf(widthDp, heightDp)
        val fraction = when {
            landscape -> 0.52f
            shortest >= 600 -> 0.30f
            heightDp >= 880 -> 0.34f
            heightDp >= 760 -> 0.37f
            heightDp >= 640 -> 0.40f
            else -> 0.44f
        }
        val budget = heightDp * fraction - stripHeight - utilHeight
        val fitted = budget / rows - rowSpacing
        return fitted.coerceIn(30f, baseKeyHeight.coerceAtLeast(30f))
    }

    fun cornerRadiusDp(context: Context, density: Float): Float {
        if (Build.VERSION.SDK_INT < 31 || density <= 0f) return 0f
        return runCatching {
            val manager = context.getSystemService(DisplayManager::class.java)
            val display = manager.getDisplay(Display.DEFAULT_DISPLAY)
            val left = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
            val right = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
            (left + right) / 2f / density
        }.getOrDefault(0f)
    }

    fun bottomRaise(cornerRadiusDp: Float, buttonNav: Boolean): Float {
        if (buttonNav) return 0f
        return (3f + cornerRadiusDp * 0.22f).coerceIn(3f, 14f)
    }

    fun bottomInset(cornerRadiusDp: Float, buttonNav: Boolean): Float {
        if (buttonNav) return 0f
        return (cornerRadiusDp * 0.35f).coerceIn(0f, 24f)
    }
}
