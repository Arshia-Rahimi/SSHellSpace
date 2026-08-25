package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val TerminalDataModule = module {
    single<ShellDiscoveryServiceImpl>() bind ShellDiscoveryService::class
}
