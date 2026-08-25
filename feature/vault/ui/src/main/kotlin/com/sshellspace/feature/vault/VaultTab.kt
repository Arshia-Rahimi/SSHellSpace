package com.sshellspace.feature.vault

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabInstance
import com.sshellspace.feature.vault.screen.VaultTabViewModel

const val VAULT_TAB_ID = "Vault"

object VaultTab : Tab

object VaultTabInstance : TabInstance {
    override val tab = VaultTab
}
