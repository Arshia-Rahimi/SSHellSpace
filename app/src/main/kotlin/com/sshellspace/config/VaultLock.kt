package com.sshellspace.config

import com.sshellspace.core.data.vault.VaultAutoLock
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.PortForwardingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.Koin

/** Shuts down everything using a vault's connections when it locks, so no session outlives the password that opened it. */
fun Koin.registerVaultLock() {
    val tabManager = get<TabManager>()

    get<VaultManager>().beforeLock {
        withContext(Dispatchers.Main) { tabManager.reset() }
        get<PortForwardingService>().stopAll()
    }
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { get<VaultAutoLock>().run() }
}
