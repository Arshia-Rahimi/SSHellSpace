package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.Tab
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabController
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModelFactory
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModelFactoryEntry
import com.github.arshia_rahimi.sshellspace.feature.settings.SettingsModule
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultTab
import com.github.arshia_rahimi.sshellspace.feature.vault.screen.VAULT_TAB_ID
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
        { tab: Tab ->
            getOrNull<TabViewModelFactoryEntry<Tab>>()
                ?.create(tab)
                ?: error("No factory registered for ${tab::class}")
        }
    }

    single {
        TabManager(
            defaultTabId = VAULT_TAB_ID,
            fixedTabs = listOf(VaultTab),
            tabViewModelFactory = get(),
        )
    } bind TabController::class
}
