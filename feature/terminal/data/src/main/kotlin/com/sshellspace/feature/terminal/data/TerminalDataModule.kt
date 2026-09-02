package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.SecretStore
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import org.apache.sshd.client.SshClient
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val TerminalDataModule = module {
    single<ShellDiscoveryServiceImpl>() bind ShellDiscoveryService::class
    single<TerminalPreferencesRepositoryImpl>() bind TerminalPreferencesRepository::class
    single<KeyringSecretStore>() bind SecretStore::class
    single { create(::createSshClient) }
    factory<MinaSshConnectionService>() bind SshConnectionService::class
}

private fun createSshClient() = SshClient.setUpDefaultClient().apply { start() }
