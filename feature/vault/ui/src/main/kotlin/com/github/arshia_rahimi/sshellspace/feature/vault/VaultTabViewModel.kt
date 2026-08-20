package com.github.arshia_rahimi.sshellspace.feature.vault

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel

class VaultTabViewModel: TabViewModel(
    id = "Vault",
    initialTitle = "Vault",
    initialIcon = null,
) {
    @Composable
    override fun screen() = VaultTabScreen()
}
