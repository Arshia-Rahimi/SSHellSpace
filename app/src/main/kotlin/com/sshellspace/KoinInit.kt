package com.sshellspace

import app.cash.sqldelight.db.SqlDriver
import com.sshellspace.core.data.DataModule
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabsModule
import com.sshellspace.feature.settings.SettingsModule
import com.sshellspace.feature.terminal.TerminalModule
import com.sshellspace.feature.vault.MainVaultTabInstance
import com.sshellspace.feature.vault.VAULT_TAB_ID
import com.sshellspace.feature.vault.VaultModule
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

fun initKoin() {
    startKoin {
        modules(CoreModule, AppModule)
    }
}

val CoreModule = module {
    includes(
        DataModule,
        TabsModule,
    )
}

val AppModule = module {
    single { getKoin() }
    single { create(::createSqliteDriver) } bind SqlDriver::class
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
