package com.github.arshia_rahimi.sshellspace.feature.vault

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModelFactoryEntry
import com.github.arshia_rahimi.sshellspace.feature.vault.screen.VaultTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val VaultModule = module {
    single(named<VaultTab>()) {
        TabViewModelFactoryEntry<VaultTab> { _ -> VaultTabViewModel() }
    }
}
