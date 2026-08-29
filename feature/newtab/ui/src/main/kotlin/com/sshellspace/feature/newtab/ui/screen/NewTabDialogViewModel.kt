package com.sshellspace.feature.newtab.ui.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NewTabDialogViewModel(
    private val tabManager: TabManager,
    shellDiscoveryService: ShellDiscoveryService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NewTabDialogState(
            discoveredShells = shellDiscoveryService.discover(),
        )
    )
    val uiState = _uiState.asStateFlow()

    fun execute(action: NewTabDialogAction) {
        when (action) {
            is NewTabDialogAction.CreateNewTab -> createNewTab(action.shell)
        }
    }

    private fun createNewTab(shell: Shell) {
        tabManager.createNewTab(TerminalParams(shell))
    }
}
