package com.sshellspace.feature.terminal.ui.screen

import ai.rever.bossterm.compose.EmbeddableTerminalState
import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import org.koin.core.annotation.InjectedParam

class TerminalTabViewModel(
    @InjectedParam private val tabController: TabController,
    @InjectedParam private val shell: Shell,
    terminalPreferencesRepository: TerminalPreferencesRepository,
) : ViewModel() {
    private val terminalPrefs = terminalPreferencesRepository.get().value

    val terminalState = EmbeddableTerminalState()

}
