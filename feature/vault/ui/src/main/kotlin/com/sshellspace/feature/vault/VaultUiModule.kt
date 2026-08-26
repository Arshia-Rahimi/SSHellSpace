package com.sshellspace.feature.vault

import com.sshellspace.core.ui.tabs.registerViewModel
import com.sshellspace.feature.vault.screen.VaultTabViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

val VaultUiModule = module {
    VaultTabType.registerViewModel { create(::VaultTabViewModel) }
}
