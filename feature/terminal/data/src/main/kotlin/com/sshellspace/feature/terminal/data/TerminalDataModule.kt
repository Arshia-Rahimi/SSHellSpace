package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.SecretStore
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val TerminalDataModule = module {
    single<ShellDiscoveryServiceImpl>() bind ShellDiscoveryService::class
    single<TerminalPreferencesRepositoryImpl>() bind TerminalPreferencesRepository::class
    single<KeyringSecretStore>() bind SecretStore::class
}
