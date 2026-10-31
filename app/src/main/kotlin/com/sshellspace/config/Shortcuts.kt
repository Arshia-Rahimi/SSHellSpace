package com.sshellspace.config

import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.domain.models.resolveDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.Koin

private val shortcutScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Binds an action to every [AppShortcut]. */
fun Koin.registerShortcuts() {
    val shortcutHandler = get<ShortcutHandler>()
    val tabManager = get<TabManager>()
    val terminalPreferences = get<TerminalPreferencesRepository>()
    val shellDiscoveryService = get<ShellDiscoveryService>()

    shortcutHandler.register(AppShortcut.NewTab) {
        shortcutScope.launch {
            val shell = withContext(Dispatchers.IO) {
                shellDiscoveryService.discover().resolveDefault(terminalPreferences.get().value.defaultShell)
            }
            tabManager.createNewTab(TerminalParams(shell))
        }
    }
}
