package com.sshellspace.config

import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.PortForwardingService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decides what happens to terminals and port forwards as vaults lock and unlock. A lock normally only hides them, so
 * work carries on and is still there on unlock. They belong to the vault that opened them, though, so opening a different
 * vault closes them before it appears.
 */
class VaultSessions(
    private val tabManager: TabManager,
    private val portForwarding: () -> PortForwardingService,
    private val terminalPreferences: TerminalPreferencesRepository,
) {
    private var ownerVaultId: Uuid? = null

    suspend fun onLocking() {
        if (terminalPreferences.get().value.keepSessionsWhenLocked) {
            // Live sessions need nothing from the vault, but these pages hold its hosts, keys and passwords.
            withContext(Dispatchers.Main) { tabManager.clearFixedTabs() }
        } else {
            closeAll()
        }
    }

    suspend fun onUnlocking(vault: VaultInfo) {
        if (ownerVaultId != vault.id) closeAll()
        ownerVaultId = vault.id
    }

    suspend fun onDeleted(id: Uuid) {
        if (ownerVaultId != id) return
        closeAll()
        ownerVaultId = null
    }

    private suspend fun closeAll() {
        withContext(Dispatchers.Main) { tabManager.reset() }
        portForwarding().stopAll()
    }
}
