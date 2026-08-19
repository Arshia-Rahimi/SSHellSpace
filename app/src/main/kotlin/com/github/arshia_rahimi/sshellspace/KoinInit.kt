package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import com.github.arshia_rahimi.sshellspace.core.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.feature.newtab.NewTabViewModel
import com.github.arshia_rahimi.sshellspace.feature.newtab.NewTabViewModelModule
import com.github.arshia_rahimi.sshellspace.feature.terminal.SettingsModule
import com.github.arshia_rahimi.sshellspace.feature.terminal.SettingsTabViewModel
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultTabViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

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
        create<TabManager> {
            val vaultTab: VaultTabViewModel = get()
            val settingsTab: SettingsTabViewModel = get()
            val newTabFactory = { get<NewTabViewModel>() }
            TabManager(
                defaultTabId = vaultTab.id,
                fixedTabs = listOf(settingsTab, vaultTab),
                tabViewModelFactory = newTabFactory,
            )
        }
    }
}
