package com.github.arshia_rahimi.sshellspace.feature.vault

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val VaultModule = module {
    single<VaultTabViewModel>()
}
