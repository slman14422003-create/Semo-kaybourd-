package com.semo.keyboard.util

import android.content.Context
import android.provider.Settings

object NavigationMode {

    private const val GESTURE_FLAG = 1
    private const val MODE_GESTURE = 2

    fun usesButtons(context: Context): Boolean {
        val resolver = context.contentResolver
        val signals = ArrayList<Boolean>(3)
        runCatching { Settings.Global.getInt(resolver, "navigation_bar_gesture_while_hidden") }
            .getOrNull()?.let { signals.add(it != GESTURE_FLAG) }
        runCatching { Settings.Secure.getInt(resolver, "navigation_mode") }
            .getOrNull()?.let { signals.add(it < MODE_GESTURE) }
        configMode(context)?.let { signals.add(it < MODE_GESTURE) }
        val buttons = signals.count { it }
        val gestures = signals.size - buttons
        return buttons > gestures
    }

    private fun configMode(context: Context): Int? = runCatching {
        val res = context.resources
        val id = res.getIdentifier("config_navBarInteractionMode", "integer", "android")
        if (id == 0) null else res.getInteger(id)
    }.getOrNull()
}
