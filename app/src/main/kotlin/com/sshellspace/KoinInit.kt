package com.sshellspace

import app.cash.sqldelight.db.SqlDriver
import com.sshellspace.config.DEFAULT_TAB_ID
import com.sshellspace.config.createSqliteDriver
import com.sshellspace.config.fixedTabItems
import com.sshellspace.core.data.DataModule
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.newtab.ui.NewTabUiModule
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
        VaultUiModule,
        SettingsUiModule,
        TerminalUiModule,
        TerminalDataModule,
        NewTabUiModule,
    )

    single { getKoin() }
    single { create(::createSqliteDriver) } bind SqlDriver::class
    single {
        TabManagerImpl(
            defaultTabId = DEFAULT_TAB_ID,
            fixedTabs = fixedTabItems,
        )
    } bind TabManager::class
}
