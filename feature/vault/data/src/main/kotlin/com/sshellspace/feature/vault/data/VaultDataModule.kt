package com.sshellspace.feature.vault.data

import com.sshellspace.feature.vault.domain.SshKeyRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val VaultDataModule = module {
    single<SshKeyRepositoryImpl>() bind SshKeyRepository::class
}
