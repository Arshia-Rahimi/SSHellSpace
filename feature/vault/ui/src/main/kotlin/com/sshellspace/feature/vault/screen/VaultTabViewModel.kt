package com.sshellspace.feature.vault.screen

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.tabs.TabViewModel
import com.sshellspace.feature.vault.VAULT_TAB_ID


class VaultTabViewModel: TabViewModel(
    id = VAULT_TAB_ID,
    initialTitle = VAULT_TAB_ID,
    initialIcon = null,
) {
    @Composable
    override fun screen() = VaultTabScreen()
}
