package com.ios26.keyboard.util

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * يقرأ حالة اللوحة من إعدادات النظام مباشرة (بلا حاجة لأي صلاحية خاصة)،
 * لعرض حالة حية أثناء معالج الإعداد: هل هي مفعّلة؟ وهل هي اللوحة المختارة حاليًا؟
 */
object KeyboardStatusHelper {

    private const val PACKAGE_NAME = "com.ios26.keyboard"
    private const val SERVICE_NAME = "$PACKAGE_NAME.ime.IOSKeyboardService"

    fun isKeyboardEnabled(context: Context): Boolean = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList.orEmpty().any { it.packageName == PACKAGE_NAME }
    }.getOrDefault(false)

    fun isKeyboardSelected(context: Context): Boolean = runCatching {
        val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        defaultIme?.startsWith(PACKAGE_NAME) == true || defaultIme == "$PACKAGE_NAME/$SERVICE_NAME"
    }.getOrDefault(false)
}
