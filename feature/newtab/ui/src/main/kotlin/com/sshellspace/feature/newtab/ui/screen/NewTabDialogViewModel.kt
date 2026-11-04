package com.sshellspace.feature.newtab.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.shortcuts.label
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.SftpParams
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.domain.models.resolveDefault
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewTabDialogViewModel(
    private val tabManager: TabManager,
    private val shellDiscoveryService: ShellDiscoveryService,
    terminalPreferencesRepository: TerminalPreferencesRepository,
    shortcutHandler: ShortcutHandler,
    connectionRepository: SshConnectionRepository,
) : ViewModel() {
    private val discoveredShells = MutableStateFlow<List<Shell>?>(null)

    val uiState = combine(
        discoveredShells,
        terminalPreferencesRepository.get(),
        shortcutHandler.bindings,
        connectionRepository.getConnections(),
    ) { shells, terminalPrefs, bindings, connections ->
        if (shells == null) return@combine NewTabDialogState(connections = connections, isLoading = true)
        val defaultShell = shells.resolveDefault(terminalPrefs.defaultShell)
        NewTabDialogState(
            defaultShell = defaultShell,
            otherShells = shells.filter { it.command != defaultShell.command },
            connections = connections,
            newTabShortcutLabel = bindings[AppShortcut.NewTab]?.label,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), NewTabDialogState())

    init {
        viewModelScope.launch {
            val shells = withContext(Dispatchers.IO) {
                runCatching { shellDiscoveryService.discover() }.getOrDefault(emptyList())
            }
            discoveredShells.update { shells }
        }
    }

    fun execute(action: NewTabDialogAction) {
        when (action) {
            is NewTabDialogAction.CreateNewTab -> createNewTab(action.shell)
            is NewTabDialogAction.Connect -> connect(action.connection)
            is NewTabDialogAction.BrowseFiles -> tabManager.createNewTab(
                SftpParams(connectionId = action.connection.id, title = action.connection.name),
            )
        }
    }

    private fun createNewTab(shell: Shell) {
        tabManager.createNewTab(TerminalParams(shell))
    }

    private fun connect(connection: SshConnection) {
        tabManager.createNewTab(SshTerminalParams(connectionId = connection.id, title = connection.name))
    }
}
