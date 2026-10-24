package com.semo.keyboard.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * يقرأ حالة اللوحة من النظام (بدون أي صلاحية): هل هي مفعّلة؟ وهل هي المختارة حاليًا؟
 *
 * بعض الأجهزة (سامسونج/شاومي/أندرويد 14+) ترجع نتيجة فارغة أو ترمي استثناء عند قراءة إعدادات الـ IME.
 * لذلك كل مصدر يُفحص باستقلال وبثلاث نتائج: نعم / لا / غير معروف (تعذّرت القراءة).
 *
 * - «نعم» من أي مصدر = مفعّلة.
 * - «لا» من مصدر مقروء = غير مفعّلة، حتى لو كانت اشتغلت قبل ذلك (كان العلم القديم «شوهدت»
 *   يبقي الحالة «مفعّلة» للأبد حتى بعد أن يلغي المستخدم تفعيلها من النظام).
 * - لا نعتمد على إشارة الخدمة المحفوظة إلا لو تعذّرت قراءة كل المصادر.
 */
object KeyboardStatusHelper {

    private const val PREFS = "semo_ime_status"
    private const val KEY_SEEN = "ime_seen"

    private enum class Probe { YES, NO, UNKNOWN }

    /** @Volatile لأن الخدمة والتطبيق بنفس العملية، فالقراءة فورية بدون انتظار القرص */
    @Volatile private var visibleNow = false

    // ---------- إشارات من الخدمة ----------

    fun markImeSeen(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_SEEN, true).apply() }
    }

    fun setImeVisible(context: Context, visible: Boolean) {
        visibleNow = visible
        if (visible) markImeSeen(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- أدوات مساعدة ----------

    /** يتحقق أن معرّف الـ IME (مثل com.x/.Svc أو com.x/com.x.Svc;subtype) يخص تطبيقنا */
    private fun idBelongsTo(rawId: String, pkg: String): Boolean {
        val id = rawId.substringBefore(';').trim()
        if (id.isEmpty()) return false
        val component = runCatching { ComponentName.unflattenFromString(id) }.getOrNull()
        return if (component != null) component.packageName == pkg else id.startsWith("$pkg/")
    }

    private fun probeEnabledViaSecure(context: Context): Probe = runCatching {
        val ids = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
        if (ids.isNullOrBlank()) Probe.UNKNOWN
        else if (ids.split(':').any { idBelongsTo(it, context.packageName) }) Probe.YES
        else Probe.NO
    }.getOrDefault(Probe.UNKNOWN)

    private fun probeEnabledViaManager(context: Context): Probe = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val list = imm?.enabledInputMethodList.orEmpty()
        if (list.isEmpty()) Probe.UNKNOWN
        else if (list.any { it.packageName == context.packageName }) Probe.YES
        else Probe.NO
    }.getOrDefault(Probe.UNKNOWN)

    // ---------- الفحص ----------

    fun isKeyboardEnabled(context: Context): Boolean {
        val secure = probeEnabledViaSecure(context)
        if (secure == Probe.YES) return true
        val manager = probeEnabledViaManager(context)
        if (manager == Probe.YES) return true
        // اللوحة ظاهرة الآن أو هي الافتراضية = بالضرورة مفعّلة
        if (isKeyboardSelected(context)) return true
        // لا نصدّق العلم المحفوظ إلا لو تعذّرت قراءة المصدرين كلاهما
        if (secure == Probe.UNKNOWN && manager == Probe.UNKNOWN) {
            return runCatching { prefs(context).getBoolean(KEY_SEEN, false) }.getOrDefault(false)
        }
        return false
    }

    fun isKeyboardSelected(context: Context): Boolean {
        val viaSecure = runCatching {
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            !current.isNullOrBlank() && idBelongsTo(current, context.packageName)
        }.getOrDefault(false)
        if (viaSecure) return true
        // اللوحة ظاهرة الآن على الشاشة = هي المستخدمة حاليًا
        return visibleNow
    }
}
