package com.sshellspace.feature.newtab.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.shortcuts.label
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.domain.models.resolveDefault
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
) : ViewModel() {
    private val discoveredShells = MutableStateFlow<List<Shell>?>(null)

    val uiState = combine(
        discoveredShells,
        terminalPreferencesRepository.get(),
        shortcutHandler.bindings,
    ) { shells, terminalPrefs, bindings ->
        if (shells == null) return@combine NewTabDialogState(isLoading = true)
        val defaultShell = shells.resolveDefault(terminalPrefs.defaultShell)
        NewTabDialogState(
            defaultShell = defaultShell,
            otherShells = shells.filter { it.command != defaultShell.command },
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
        }
    }

    private fun createNewTab(shell: Shell) {
        tabManager.createNewTab(TerminalParams(shell))
    }
}
