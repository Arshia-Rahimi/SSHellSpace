package com.sshellspace.config

import com.sshellspace.core.data.vault.VaultAutoLock
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.feature.terminal.domain.PortForwardingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin

/** Connects what happens to running sessions, and the background jobs, to the vault lifecycle. */
fun Koin.registerVaultLock() {
    val sessions = VaultSessions(
        tabManager = get(),
        portForwarding = { get<PortForwardingService>() },
        terminalPreferences = get(),
    )
    get<VaultManager>().apply {
        beforeLock(sessions::onLocking)
        beforeUnlock(sessions::onUnlocking)
        afterDelete(sessions::onDeleted)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch { get<VaultAutoLock>().run() }
    scope.launch { get<SessionRestore>().run() }
}
