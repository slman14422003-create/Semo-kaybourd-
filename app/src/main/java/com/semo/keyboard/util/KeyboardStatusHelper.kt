package com.semo.keyboard.util

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** يقرأ حالة اللوحة من النظام (بدون أي صلاحية): هل هي مفعّلة؟ وهل هي المختارة حاليًا؟ */
object KeyboardStatusHelper {

    private const val PACKAGE_NAME = "com.semo.keyboard"

    fun isKeyboardEnabled(context: Context): Boolean = runCatching {
        val enabledIds = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        if (enabledIds?.split(':')?.any { it.startsWith("$PACKAGE_NAME/") } == true) return@runCatching true
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList.orEmpty().any { it.packageName == PACKAGE_NAME }
    }.getOrDefault(false)

    fun isKeyboardSelected(context: Context): Boolean = runCatching {
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        current?.startsWith("$PACKAGE_NAME/") == true
    }.getOrDefault(false)
}
