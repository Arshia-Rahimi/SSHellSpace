package com.sshellspace.feature.vault

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.vault.domain.SshKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.InjectedParam

class VaultTabViewModel : ViewModel() {
    private val _page = MutableStateFlow(VaultTabPage.SshKeys)
    val page = _page.asStateFlow()

    fun changePage(page: VaultTabPage) {
        _page.update { page }
    }
}
