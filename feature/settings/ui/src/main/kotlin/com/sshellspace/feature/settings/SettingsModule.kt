package com.sshellspace.feature.settings

import com.sshellspace.core.ui.tabs.TabViewModel
import com.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val SettingsModule = module {
    single(named(SettingsTab.qualifier)) { SettingsTabViewModel() } bind TabViewModel::class
}
