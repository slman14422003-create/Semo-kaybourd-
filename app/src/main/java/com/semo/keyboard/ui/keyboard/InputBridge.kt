package com.semo.keyboard.ui.keyboard

/** الجسر بين منطق اللوحة (ViewModel) وحقل الإدخال الفعلي الذي تديره خدمة الـ IME */
interface InputBridge {
    fun commitText(text: String)
    fun deleteBackward()
    fun performEnter()
    fun switchKeyboard()
    fun hideKeyboard()
    fun textBeforeCursor(length: Int): String
    fun keyFeedback(sound: Boolean, haptic: Boolean)
}
