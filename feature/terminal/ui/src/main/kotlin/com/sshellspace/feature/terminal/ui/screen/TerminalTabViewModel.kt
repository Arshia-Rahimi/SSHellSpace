package com.sshellspace.feature.terminal.ui.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal
import com.sshellspace.feature.terminal.ui.screen.emulators.factory

class TerminalTabViewModel(
    private val tabController: TabController,
    private val shell: Shell,
    terminalPreferencesRepository: TerminalPreferencesRepository,
) : ViewModel() {
    private val terminalPrefs = terminalPreferencesRepository.get().value

    // Picked once: changing the setting only affects tabs opened afterwards.
    val terminal: Terminal = terminalPrefs.defaultTerminalEmulator.factory.local(shell)

    init {
        tabController.setTextInput(terminal::sendText)
    }

    override fun onCleared() {
        super.onCleared()
        tabController.setTextInput(null)
        terminal.clear()
    }
}
