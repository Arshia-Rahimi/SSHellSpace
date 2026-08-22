package com.github.arshia_rahimi.sshellspace.feature.settings

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModelFactoryEntry
import com.github.arshia_rahimi.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val SettingsModule = module {
    single(named<SettingsTab>()) {
        TabViewModelFactoryEntry<SettingsTab> { _ -> SettingsTabViewModel() }
    }
}
