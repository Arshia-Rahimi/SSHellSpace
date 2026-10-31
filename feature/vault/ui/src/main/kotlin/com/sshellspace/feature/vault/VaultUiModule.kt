package com.sshellspace.feature.vault

import com.sshellspace.feature.vault.connections.ConnectionsPageViewModel
import com.sshellspace.feature.vault.settings.SettingsPageViewModel
import com.sshellspace.feature.vault.sshkeys.SshKeysPageViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val VaultUiModule = module {
    single<VaultTabViewModel>()
    viewModel<ConnectionsPageViewModel>()
    viewModel<SshKeysPageViewModel>()
    viewModel<SettingsPageViewModel>()
}
