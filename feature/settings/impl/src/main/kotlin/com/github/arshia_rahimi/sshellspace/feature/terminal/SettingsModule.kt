package com.github.arshia_rahimi.sshellspace.feature.terminal

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val SettingsModule = module {
    single<SettingsTabViewModel>()
}
