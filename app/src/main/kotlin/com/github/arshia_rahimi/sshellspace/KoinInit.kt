package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import com.github.arshia_rahimi.sshellspace.core.tabs.TabController
import com.github.arshia_rahimi.sshellspace.core.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.feature.newtab.NewTabViewModel
import com.github.arshia_rahimi.sshellspace.feature.newtab.NewTabViewModelModule
import com.github.arshia_rahimi.sshellspace.feature.terminal.SettingsModule
import com.github.arshia_rahimi.sshellspace.feature.terminal.SettingsTabViewModel
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultTabViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

fun initKoin() {
    startKoin {
        modules(CoreModule, AppModule)
    }
}

val CoreModule = module {
    includes(
        DatabaseModule,
        DataModule,
    )
}

val AppModule = module {
    includes(
        VaultModule,
        SettingsModule,
        NewTabViewModelModule,
        TerminalModule,
    )

    single<TabManager> {
        val vaultTab: VaultTabViewModel = get()
        val settingsTab: SettingsTabViewModel = get()
        TabManager(
            defaultTabId = vaultTab.id,
            fixedTabs = listOf(settingsTab, vaultTab),
            newTabViewModelFactory = { get<NewTabViewModel>() },
        )
    } bind TabController::class
}
