package com.sshellspace.feature.vault

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabInstance
import com.sshellspace.feature.vault.screen.VaultTabViewModel

const val VAULT_TAB_ID = "Vault"

val VaultTab = Tab(
    qualifier = VAULT_TAB_ID,
    viewModelKClass = VaultTabViewModel::class,
)

object VaultTabInstance : TabInstance {
    override val tab = VaultTab
}
