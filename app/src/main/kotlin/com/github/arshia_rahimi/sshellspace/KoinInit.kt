package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabController
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabFactoryEntry
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabType
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModelFactory
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
        TerminalModule,
    )

    single<TabViewModelFactory> {
        { type: TabType ->
            getAll<TabFactoryEntry>()
                .firstOrNull { it.type.isInstance(type) }
                ?.create
                ?.invoke(type)
                ?: error("No factory registered for ${type::class}")
        }
    }

    single<TabManager> {
        TabManager(
            defaultTabId = vaultTab.id,
            fixedTabs = listOf(settingsTab, vaultTab),
            tabViewModelFactory = get(),
        )
    } bind TabController::class
}
