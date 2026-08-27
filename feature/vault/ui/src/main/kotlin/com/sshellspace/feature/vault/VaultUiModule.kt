package com.sshellspace.feature.vault

import com.sshellspace.feature.vault.screen.VaultTabViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val VaultUiModule = module {
    single<VaultTabViewModel>()
}
