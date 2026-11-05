package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Port_forwards
import com.sshellspace.core.data.generated.Vault_ssh_keys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightKnownHostDatasourceTest {

    private fun database(driver: JdbcSqliteDriver) = Database(
        driver = driver,
        device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
        vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
        port_forwardsAdapter = Port_forwards.Adapter(EnumColumnAdapter()),
    )

    private val datasource = SqlDelightKnownHostDatasource(
        database(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(Database.Schema::create)),
    )

    @Test
    fun `adds lines once and lists them`() = runTest {
        datasource.add(listOf("b key", "a key", "b key"))
        datasource.add(listOf("a key"))

        assertEquals(listOf("a key", "b key"), datasource.lines())
        assertEquals(listOf("a key", "b key"), datasource.index().first())
    }

    @Test
    fun `replaces and deletes a line`() = runTest {
        datasource.add(listOf("a old", "b key"))

        datasource.replace("a old", "a new")
        assertEquals(listOf("a new", "b key"), datasource.lines())

        datasource.delete("b key")
        assertEquals(listOf("a new"), datasource.lines())
    }

    @Test
    fun `a vault from before this list gets the table when it migrates`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        Database.Schema.migrate(driver, oldVersion = Database.Schema.version - 1, newVersion = Database.Schema.version)

        val migrated = SqlDelightKnownHostDatasource(database(driver))
        migrated.add(listOf("a key"))
        assertEquals(listOf("a key"), migrated.lines())
    }
}
