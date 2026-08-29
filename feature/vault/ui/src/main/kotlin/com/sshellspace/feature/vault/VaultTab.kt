package com.sshellspace.feature.vault

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.vault.screen.VaultTabScreen
import com.sshellspace.feature.vault.screen.VaultTabViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

const val VAULT_TAB_ID = "Vault"

object VaultTab: TabType

val VaultTabItem = TabItem(
    tabId = VAULT_TAB_ID,
    type = VaultTab,
    initialTitle = "Vault",
    initialIcon = null,
    contentProvider = { item ->
        val viewModel: VaultTabViewModel = koinViewModel {
            parametersOf(item as TabController)
        }
        VaultTabScreen(viewModel)
    }
)
