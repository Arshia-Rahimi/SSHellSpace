package com.sshellspace.core.data

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Port_forwards
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.data.impl.CorePreferencesRepositoryImpl
import com.sshellspace.core.data.impl.MinaSshKeyService
import com.sshellspace.core.data.impl.SqlDelightPortForwardDatasource
import com.sshellspace.core.data.impl.SqlDelightPreferencesDatasource
import com.sshellspace.core.data.impl.SqlDelightSshConnectionDatasource
import com.sshellspace.core.data.impl.SqlDelightSshKeyDatasource
import com.sshellspace.core.data.vault.VaultSqlDriver
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

private const val APP_DATABASE = "app"

val DataModule = module {
    single { SqlDelightPreferencesDatasource(get(named(APP_DATABASE))) } bind PreferencesDatasource::class
    single<SqlDelightSshKeyDatasource>() bind SshKeyDatasource::class
    single<SqlDelightSshConnectionDatasource>() bind SshConnectionDatasource::class
    single<SqlDelightPortForwardDatasource>() bind PortForwardDatasource::class
    single<CorePreferencesRepositoryImpl>() bind CorePreferencesRepository::class
    single<MinaSshKeyService>() bind SshKeyService::class
    //
    single { VaultSqlDriver() }
    // Preferences live in the unencrypted app database, since the theme has to apply before any vault is unlocked.
    single(named(APP_DATABASE)) { database(get<SqlDriver>()) }
    // Everything else reads the unlocked vault.
    single { database(get<VaultSqlDriver>()) }
}

private fun database(driver: SqlDriver) = Database(
    driver = driver,
    device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
    vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
    port_forwardsAdapter = Port_forwards.Adapter(EnumColumnAdapter()),
)
