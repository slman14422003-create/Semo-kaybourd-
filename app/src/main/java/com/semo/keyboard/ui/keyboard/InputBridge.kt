package com.semo.keyboard.ui.keyboard

import com.semo.keyboard.domain.model.EditAction

/** نوع صوت/اهتزاز الضغطة: عادي، حذف، أو مفاتيح وظيفية (مسافة/Shift/Return...) مثل آيفون */
enum class KeyFeedback { STANDARD, DELETE, MODIFIER }

/** الجسر بين منطق اللوحة (ViewModel) وحقل الإدخال الفعلي الذي تديره خدمة الـ IME */
interface InputBridge {
    fun commitText(text: String)
    fun deleteBackward()
    /** يحذف [count] حرفًا قبل المؤشر (لاستبدال الكلمة الجارية بالاقتراح) */
    fun deleteSurrounding(count: Int)
    fun performEnter()
    fun switchKeyboard()
    /** يخفي لوحة المفاتيح (نفس عمل سهم الإخفاء بشريط التنقل) */
    fun hideKeyboard()
    fun textBeforeCursor(length: Int): String
    fun keyFeedback(sound: Boolean, haptic: Boolean, kind: KeyFeedback = KeyFeedback.STANDARD, volume: Float = 1f)
    /** تحريك المؤشر: قيمة موجبة = يمين، سالبة = يسار */
    fun moveCursor(delta: Int)
    /** تحريك المؤشر سطرًا لأعلى/لأسفل: قيمة موجبة = لأسفل، سالبة = لأعلى */
    fun moveCursorVertical(lines: Int)
    fun performEdit(action: EditAction)
    /** الانتقال للإدخال الصوتي بالنظام (لوحة صوتية مفعّلة بالجهاز) */
    fun startVoiceInput()
    fun openSettings()
}
