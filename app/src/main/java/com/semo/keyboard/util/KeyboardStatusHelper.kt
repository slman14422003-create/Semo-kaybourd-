package com.semo.keyboard.util

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * يقرأ حالة اللوحة من النظام (بدون أي صلاحية): هل هي مفعّلة؟ وهل هي المختارة حاليًا؟
 *
 * بعض الأجهزة (سامسونج/شاومي/أندرويد 14+) ترجع نتيجة فارغة أو ترمي استثناء عند قراءة
 * إعدادات الـ IME، وكان هذا سبب أن الحالة تبقى «غير مفعّلة» رغم أن اللوحة تعمل فعلًا.
 * لذلك كل مصدر يُفحص بشكل مستقل، ونضيف إشارة مباشرة من الخدمة نفسها: لو ظهرت اللوحة فعلًا
 * فهي مفعّلة، ولو هي ظاهرة الآن فهي المختارة.
 */
object KeyboardStatusHelper {

    private const val PREFS = "semo_ime_status"
    private const val KEY_SEEN = "ime_seen"
    private const val KEY_VISIBLE_AT = "ime_visible_at"

    /** @Volatile لأن الخدمة والتطبيق بنفس العملية، فالقراءة فورية بدون انتظار القرص */
    @Volatile private var visibleNow = false

    // ---------- إشارات من الخدمة ----------

    fun markImeSeen(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_SEEN, true).apply() }
    }

    fun setImeVisible(context: Context, visible: Boolean) {
        visibleNow = visible
        runCatching {
            prefs(context).edit()
                .putLong(KEY_VISIBLE_AT, if (visible) System.currentTimeMillis() else 0L)
                .apply()
            if (visible) prefs(context).edit().putBoolean(KEY_SEEN, true).apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- الفحص ----------

    fun isKeyboardEnabled(context: Context): Boolean {
        val pkg = context.packageName

        val viaSecure = runCatching {
            val ids = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            ids?.split(':')?.any { it.startsWith("$pkg/") } == true
        }.getOrDefault(false)
        if (viaSecure) return true

        val viaManager = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList.orEmpty().any { it.packageName == pkg }
        }.getOrDefault(false)
        if (viaManager) return true

        // لو اللوحة اشتغلت فعلًا مرة واحدة فهي مفعّلة
        return isKeyboardSelected(context) || runCatching { prefs(context).getBoolean(KEY_SEEN, false) }.getOrDefault(false)
    }

    fun isKeyboardSelected(context: Context): Boolean {
        val pkg = context.packageName

        val viaSecure = runCatching {
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            current?.startsWith("$pkg/") == true
        }.getOrDefault(false)
        if (viaSecure) return true

        // اللوحة ظاهرة الآن على الشاشة = هي المستخدمة حاليًا
        return visibleNow
    }
}
