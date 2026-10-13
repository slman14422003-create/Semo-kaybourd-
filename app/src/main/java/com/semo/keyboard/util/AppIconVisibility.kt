package com.semo.keyboard.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * يتحكّم بظهور أيقونة التطبيق بالشاشة الرئيسية وشاشة التطبيقات.
 *
 * الحيلة: نشاط ".ui.MainActivity" الحقيقي ما عنده أي intent-filter LAUNCHER بالمانيفست،
 * وبدله في alias منفصل ("LauncherAlias") هو يلي حامل أيقونة الإطلاق. تعطيل الـ alias
 * يخفي الأيقونة فورًا من اللانشر بدون أي تأثير على عمل لوحة المفاتيح (الخدمة IME منفصلة
 * تمامًا ولا علاقة لها بهاد المكوّن إطلاقًا).
 */
object AppIconVisibility {

    private const val ALIAS_CLASS = "com.semo.keyboard.ui.MainActivityLauncher"

    private fun alias(context: Context): ComponentName =
        ComponentName(context.applicationContext, ALIAS_CLASS)

    fun isIconVisible(context: Context): Boolean {
        val pm = context.applicationContext.packageManager
        val state = runCatching { pm.getComponentEnabledSetting(alias(context)) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setIconVisible(context: Context, visible: Boolean) {
        val pm = context.applicationContext.packageManager
        val newState = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            pm.setComponentEnabledSetting(alias(context), newState, PackageManager.DONT_KILL_APP)
        }
    }
}
