package com.ios26.keyboard.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ios26.keyboard.data.SettingsRepository

class KeyboardViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val onCommitText: (String) -> Unit,
    private val onDeleteBackward: () -> Unit,
    private val onCommitEnter: () -> Unit,
    private val onSwitchInputMethod: () -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return KeyboardViewModel(
            settingsRepository, onCommitText, onDeleteBackward, onCommitEnter, onSwitchInputMethod
        ) as T
    }
}
