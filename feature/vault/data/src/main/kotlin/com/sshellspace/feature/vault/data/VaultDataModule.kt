package com.sshellspace.feature.vault.data

import com.sshellspace.feature.vault.domain.KnownHostRepository
import com.sshellspace.feature.vault.domain.PortForwardRepository
import com.sshellspace.feature.vault.domain.SnippetRepository
import com.sshellspace.feature.vault.domain.SshConfigImporter
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import com.sshellspace.feature.vault.domain.SshKeyRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val VaultDataModule = module {
    single<SshKeyRepositoryImpl>() bind SshKeyRepository::class
    single<SshConnectionRepositoryImpl>() bind SshConnectionRepository::class
    single<PortForwardRepositoryImpl>() bind PortForwardRepository::class
    single<SnippetRepositoryImpl>() bind SnippetRepository::class
    single { SshConfigImporterImpl(connectionRepository = get()) } bind SshConfigImporter::class
    single { KnownHostRepositoryImpl(datasource = get()) } bind KnownHostRepository::class
}
