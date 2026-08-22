package com.github.arshia_rahimi.sshellspace.feature.vault

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel
import com.github.arshia_rahimi.sshellspace.feature.vault.screen.VaultTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val VaultModule = module {
    single(named(VaultTabQualifier.value)) { VaultTabViewModel() } bind TabViewModel::class
}
