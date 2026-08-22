package com.github.arshia_rahimi.sshellspace.feature.settings

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel
import com.github.arshia_rahimi.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val SettingsModule = module {
    single(named(SettingsTabQualifier.value)) { SettingsTabViewModel() } bind TabViewModel::class
}
