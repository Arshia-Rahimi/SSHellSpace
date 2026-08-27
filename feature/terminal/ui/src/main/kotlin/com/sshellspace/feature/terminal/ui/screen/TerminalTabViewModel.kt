package com.sshellspace.feature.terminal.ui.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository

class TerminalTabViewModel(
    terminalPreferencesRepository: TerminalPreferencesRepository,
) : ViewModel() {
    private val terminalPrefs = terminalPreferencesRepository.get().value

}
