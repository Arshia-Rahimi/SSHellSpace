package com.sshellspace.feature.vault

import com.sshellspace.feature.vault.screen.VaultTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val VaultModule = module {
    single(named(VaultTab.qualifier)) { VaultTabViewModel() }
}
