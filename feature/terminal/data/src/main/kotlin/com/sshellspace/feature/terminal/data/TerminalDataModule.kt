package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.PortForwardingService
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.data.ssh.agent.SshAgentLocator
import org.apache.sshd.client.SshClient
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val TerminalDataModule = module {
    single<ShellDiscoveryServiceImpl>() bind ShellDiscoveryService::class
    single<TerminalPreferencesRepositoryImpl>() bind TerminalPreferencesRepository::class
    single { SshAgentLocator() }
    single<SshClient> { createSshClient(agentLocator = get()) }
    factory<MinaSshConnectionService>() bind SshConnectionService::class
    single<PortForwardingService> { MinaPortForwardingService(connectionService = get(), forwardDatasource = get()) }
}
