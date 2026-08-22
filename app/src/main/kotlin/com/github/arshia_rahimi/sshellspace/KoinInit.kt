package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabController
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabsModule
import com.github.arshia_rahimi.sshellspace.feature.settings.SettingsModule
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VAULT_TAB_ID
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultModule
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultTabQualifier
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
        TabsModule,
    )
}

val AppModule = module {
    single { getKoin() }
    includes(
        VaultModule,
        SettingsModule,
        TerminalModule,
    )

    single {
        TabManager(
            defaultTabId = VAULT_TAB_ID,
            fixedTabs = listOf(VaultTabQualifier),
            tabViewModelFactory = get(),
        )
    } bind TabController::class
}
