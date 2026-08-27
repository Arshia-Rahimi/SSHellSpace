package com.sshellspace.feature.settings

import com.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val SettingsUiModule = module {
    viewModel<SettingsTabViewModel>()
}
