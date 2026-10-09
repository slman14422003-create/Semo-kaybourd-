package com.semo.keyboard.ui.keyboard

import com.semo.keyboard.domain.model.EditAction

/** الجسر بين منطق اللوحة (ViewModel) وحقل الإدخال الفعلي الذي تديره خدمة الـ IME */
interface InputBridge {
    fun commitText(text: String)
    fun deleteBackward()
    /** يحذف [count] حرفًا قبل المؤشر (لاستبدال الكلمة الجارية بالاقتراح) */
    fun deleteSurrounding(count: Int)
    fun performEnter()
    fun switchKeyboard()
    fun hideKeyboard()
    fun textBeforeCursor(length: Int): String
    fun keyFeedback(sound: Boolean, haptic: Boolean)
    /** تحريك المؤشر: قيمة موجبة = يمين، سالبة = يسار */
    fun moveCursor(delta: Int)
    fun performEdit(action: EditAction)
    fun openSettings()
}
