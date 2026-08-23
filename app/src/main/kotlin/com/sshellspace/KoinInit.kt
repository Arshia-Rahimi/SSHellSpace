package com.sshellspace

import com.sshellspace.core.data.DataModule
import com.sshellspace.core.database.DatabaseModule
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabsModule
import com.sshellspace.feature.settings.SettingsModule
import com.sshellspace.feature.terminal.TerminalModule
import com.sshellspace.feature.vault.VAULT_TAB_ID
import com.sshellspace.feature.vault.VaultModule
import com.sshellspace.feature.vault.MainVaultTabInstance
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
            fixedTabs = listOf(MainVaultTabInstance),
            tabViewModelFactory = get(),
        )
    } bind TabController::class
}
