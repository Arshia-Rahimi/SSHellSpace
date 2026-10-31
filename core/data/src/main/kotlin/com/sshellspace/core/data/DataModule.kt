package com.sshellspace.core.data

import app.cash.sqldelight.EnumColumnAdapter
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.data.impl.CorePreferencesRepositoryImpl
import com.sshellspace.core.data.impl.MinaSshKeyService
import com.sshellspace.core.data.impl.SqlDelightPreferencesDatasource
import com.sshellspace.core.data.impl.SqlDelightSshConnectionDatasource
import com.sshellspace.core.data.impl.SqlDelightSshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val DataModule = module {
    single<SqlDelightPreferencesDatasource>() bind PreferencesDatasource::class
    single<SqlDelightSshKeyDatasource>() bind SshKeyDatasource::class
    single<SqlDelightSshConnectionDatasource>() bind SshConnectionDatasource::class
    single<CorePreferencesRepositoryImpl>() bind CorePreferencesRepository::class
    single<MinaSshKeyService>() bind SshKeyService::class
    //
    single {
        Database(
            driver = get(),
            device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
            vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
        )
    }
}
