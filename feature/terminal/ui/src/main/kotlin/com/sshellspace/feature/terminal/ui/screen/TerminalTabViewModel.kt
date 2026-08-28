package com.sshellspace.feature.terminal.ui.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import org.koin.core.annotation.InjectedParam

class TerminalTabViewModel(
    @InjectedParam private val tabController: TabController,
    terminalPreferencesRepository: TerminalPreferencesRepository,
) : ViewModel() {
    private val terminalPrefs = terminalPreferencesRepository.get().value

}
