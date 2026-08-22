package com.github.arshia_rahimi.sshellspace.feature.vault

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.Tab
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabInstance
import com.github.arshia_rahimi.sshellspace.feature.vault.screen.VaultTabViewModel

const val VAULT_TAB_ID = "Vault"

val VaultTab = Tab(
    qualifier = VAULT_TAB_ID,
    viewModelKClass = VaultTabViewModel::class,
)

object MainVaultTabInstance : TabInstance {
    override val tab = VaultTab
}
