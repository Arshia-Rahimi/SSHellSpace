package com.sshellspace.feature.settings

import com.sshellspace.core.ui.tabs.registerViewModel
import com.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

val SettingsUiModule = module {
    SettingsTabType.registerViewModel { create(::SettingsTabViewModel) }
}
