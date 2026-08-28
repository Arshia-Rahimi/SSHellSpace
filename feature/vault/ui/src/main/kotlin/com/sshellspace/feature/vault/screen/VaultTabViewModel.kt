package com.sshellspace.feature.vault.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabController
import org.koin.core.annotation.InjectedParam

class VaultTabViewModel(
    @InjectedParam private val tabController: TabController,
): ViewModel() {

}
