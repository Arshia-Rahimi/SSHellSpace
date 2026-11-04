package com.sshellspace

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.config.DEFAULT_TAB_ID
import com.sshellspace.config.SessionRestore
import com.sshellspace.config.createSqliteDriver
import com.sshellspace.config.fixedTabItems
import com.sshellspace.config.registerShortcuts
import com.sshellspace.config.registerVaultLock
import com.sshellspace.config.vaultsDirectory
import com.sshellspace.core.data.DataModule
import com.sshellspace.core.data.vault.VaultAutoLock
import com.sshellspace.core.data.vault.VaultManagerImpl
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.CoreUiModule
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.newtab.ui.NewTabUiModule
import com.sshellspace.feature.settings.SettingsUiModule
import com.sshellspace.feature.terminal.data.TerminalDataModule
import com.sshellspace.feature.terminal.ui.TerminalUiModule
import com.sshellspace.feature.vault.VaultUiModule
import com.sshellspace.feature.vault.data.VaultDataModule
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

fun initKoin() {
    startKoin { modules(AppModule) }.koin.apply {
        registerShortcuts()
        registerVaultLock()
    }
}

val AppModule = module {
    includes(
        DataModule,
        CoreUiModule,
        VaultUiModule,
        VaultDataModule,
        SettingsUiModule,
        TerminalUiModule,
        TerminalDataModule,
        NewTabUiModule,
    )

    single { getKoin() }
    single { create(::createSqliteDriver) } bind SqlDriver::class
    single<VaultManager> {
        VaultManagerImpl(
            directory = vaultsDirectory(),
            unencryptedDriver = get<JdbcSqliteDriver>(),
            vaultDriver = get(),
        )
    }
    single { VaultAutoLock(vaultManager = get(), preferences = get()) }
    single {
        SessionRestore(
            vaultManager = get(),
            tabManager = get(),
            savedSessions = get(),
            terminalPreferences = get(),
            connections = get(),
            shellDiscovery = get(),
        )
    }
    single {
        TabManagerImpl(
            defaultTabId = DEFAULT_TAB_ID,
            fixedTabs = fixedTabItems,
        )
    } bind TabManager::class
}
