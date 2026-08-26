package com.sshellspace.feature.vault

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.vault.screen.VaultTabScreen

const val VAULT_TAB_ID = "Vault"

object VaultTabType : TabType {
    override val isSingleInstance = true
}

object VaultTab : Tab(
    type = VaultTabType,
    id = VAULT_TAB_ID,
    initialTitle = "Vault",
    initialIcon = null,
) {
    @Composable
    override fun screen() = VaultTabScreen()
}
