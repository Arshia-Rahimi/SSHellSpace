package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.feature.newtab.NewTabComponent
import com.github.arshia_rahimi.sshellspace.feature.terminal.SettingsTabComponent
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalTabComponent
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultTabComponent
import com.github.arshia_rahimi.sshellspace.tabs.NewTabFactory
import com.github.arshia_rahimi.sshellspace.tabs.SettingsTab
import com.github.arshia_rahimi.sshellspace.tabs.VaultTab
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

fun initKoin() {
    startKoin {
        modules(
            DatabaseModule,
            DataModule,
            TabModule,
        )
    }
}

val TabModule = module {
    single<TabManager>()
    single<VaultTab>()
    single<SettingsTab>()
    single<NewTabFactory>()
    //
    single<VaultTabComponent>() bind TabComponent::class
    single<SettingsTabComponent>() bind TabComponent::class
    factory<TerminalTabComponent>() bind TabComponent::class
    factory<NewTabComponent>()
    factory<() -> TabComponent> { { get<NewTabComponent>() } }
}
