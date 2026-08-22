package com.github.arshia_rahimi.sshellspace.feature.vault.screen

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel

const val VAULT_TAB_ID = "Vault"

class VaultTabViewModel: TabViewModel(
    id = VAULT_TAB_ID,
    initialTitle = VAULT_TAB_ID,
    initialIcon = null,
) {
    @Composable
    override fun screen() = VaultTabScreen()
}
