package com.sshellspace.feature.vault

import com.sshellspace.feature.vault.sshkeys.SshKeysPageViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val VaultUiModule = module {
    single<VaultTabViewModel>()
    viewModel<SshKeysPageViewModel>()
}
