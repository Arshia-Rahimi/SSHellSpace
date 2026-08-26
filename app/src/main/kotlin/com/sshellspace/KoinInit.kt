package com.sshellspace

import app.cash.sqldelight.db.SqlDriver
import com.sshellspace.core.data.DataModule
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabsModule
import com.sshellspace.feature.settings.SettingsUiModule
import com.sshellspace.feature.terminal.data.TerminalDataModule
import com.sshellspace.feature.terminal.ui.TerminalUiModule
import com.sshellspace.feature.vault.VaultUiModule
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

fun initKoin() = startKoin { modules(AppModule) }

val AppModule = module {
    includes(
        DataModule,
        TabsModule,
        VaultUiModule,
        SettingsUiModule,
        TerminalUiModule,
        TerminalDataModule,
    )

    single { getKoin() }
    single { create(::createSqliteDriver) } bind SqlDriver::class
    single {
        TabManager(
            defaultTabId = DEFAULT_TAB_ID,
            fixedTabs = listOf(fixedTabInstance),
        )
    } bind TabController::class
}
