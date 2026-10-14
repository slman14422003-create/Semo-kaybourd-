package com.semo.keyboard.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconVisibility {

    private const val ALIAS_CLASS = "com.semo.keyboard.ui.MainActivityLauncher"

    private fun alias(context: Context) = ComponentName(context.applicationContext, ALIAS_CLASS)

    fun isIconVisible(context: Context): Boolean {
        val pm = context.applicationContext.packageManager
        val state = runCatching { pm.getComponentEnabledSetting(alias(context)) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setIconVisible(context: Context, visible: Boolean) {
        if (isIconVisible(context) == visible) return
        val pm = context.applicationContext.packageManager
        val state = if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching { pm.setComponentEnabledSetting(alias(context), state, PackageManager.DONT_KILL_APP) }
    }
}
