package com.sshellspace.config

import com.sshellspace.core.domain.models.TabParams
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.SavedSessionRepository
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.SavedSession
import com.sshellspace.feature.terminal.domain.models.SavedTab
import com.sshellspace.feature.terminal.domain.models.SftpParams
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Remembers each vault's open tabs inside that vault and reopens them when it unlocks with none open, such as after
 * restarting the app. Tabs reopened this way are new sessions: what was running in them is gone.
 */
class SessionRestore(
    private val vaultManager: VaultManager,
    private val tabManager: TabManager,
    private val savedSessions: SavedSessionRepository,
    private val terminalPreferences: TerminalPreferencesRepository,
    private val connections: SshConnectionRepository,
    private val shellDiscovery: ShellDiscoveryService,
) {
    suspend fun run() {
        // By id, since changing a vault's password also updates this and isn't a new unlock.
        vaultManager.activeVault.distinctUntilChangedBy { it?.id }.collectLatest { vault ->
            if (vault == null) return@collectLatest
            // Tabs still open means they kept running through the lock, so there's nothing to bring back.
            if (terminalPreferences.get().value.restoreTabs && tabManager.openTabs().isEmpty()) restore()
            tabManager.tabsState()
                .map { currentSession() }
                .distinctUntilChanged()
                .collect(::save)
        }
    }

    private suspend fun restore() {
        val saved = savedSessions.load()
        val allowedShells = if (saved.tabs.any { it is SavedTab.Local }) allowedShellCommands() else emptySet()
        val params = saved.tabs.mapNotNull { tab -> tab.toParams(allowedShells) }
        withContext(Dispatchers.Main) {
            params.forEach(tabManager::createNewTab)
            // Tabs whose connection was deleted are gone, so the position only holds if none were dropped.
            val current = saved.currentIndex?.takeIf { params.size == saved.tabs.size }
            val open = tabManager.openTabs()
            when {
                current != null && current in open.indices -> tabManager.setCurrentTab(open[current].first)
                open.isNotEmpty() -> tabManager.setCurrentTab(tabManager.tabsState().value.fixedTab.first().tab.id)
            }
        }
    }

    /**
     * Only shells this computer really offers can reopen, since this runs a program with nobody clicking anything.
     * Imports drop saved tabs, but a command in the file should never be what decides this.
     */
    private fun allowedShellCommands(): Set<String?> = buildSet {
        add(terminalPreferences.get().value.defaultShell.command)
        runCatching { shellDiscovery.discover() }.getOrDefault(emptyList()).mapTo(this) { it.command }
    }

    private suspend fun SavedTab.toParams(allowedShells: Set<String?>): TabParams? = when (this) {
        is SavedTab.Local -> TerminalParams(shell).takeIf { shell.command in allowedShells }
        is SavedTab.Ssh -> connections.get(connectionId)?.let { SshTerminalParams(it.id, it.name) }
        is SavedTab.Sftp -> connections.get(connectionId)?.let { SftpParams(it.id, it.name) }
        SavedTab.LocalFiles -> SftpParams.ThisDevice
    }

    private fun currentSession(): SavedSession {
        val open = tabManager.openTabs()
        val currentId = tabManager.tabsState().value.currentTabId
        return SavedSession(
            tabs = open.mapNotNull { (_, params) ->
                when (params) {
                    is SshTerminalParams -> SavedTab.Ssh(params.connectionId, params.title)
                    is SftpParams -> params.connectionId?.let { SavedTab.Sftp(it, params.title) } ?: SavedTab.LocalFiles
                    is TerminalParams -> SavedTab.Local(params.shell)
                    else -> null
                }
            },
            currentIndex = open.indexOfFirst { it.first == currentId }.takeIf { it >= 0 },
        )
    }

    private suspend fun save(session: SavedSession) {
        // Locking closes every tab, which must not count as the user closing them. The vault is marked locked first.
        if (vaultManager.activeVault.value == null || !terminalPreferences.get().value.restoreTabs) return
        try {
            savedSessions.save(session)
        } catch (_: IllegalStateException) {
            // The vault locked in the moment since the check above. Its last save stands.
        }
    }
}
