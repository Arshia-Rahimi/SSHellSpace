package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Port_forwards
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightPortForwardDatasourceTest {

    private val datasource = SqlDelightPortForwardDatasource(
        db = Database(
            driver = JdbcSqliteDriver(
                url = JdbcSqliteDriver.IN_MEMORY,
                schema = Database.Schema,
                properties = Properties(),
            ),
            device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
            vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
            port_forwardsAdapter = Port_forwards.Adapter(EnumColumnAdapter()),
        ),
    )

    private fun forward(name: String, type: PortForwardType, connectionId: Uuid = Uuid.random()) = PortForward(
        id = Uuid.random(),
        name = name,
        connectionId = connectionId,
        type = type,
        bindHost = "0.0.0.0",
        bindPort = 8080,
        destinationHost = "db.internal",
        destinationPort = 5432,
    )

    @Test
    fun `every forward type survives a round trip`() = runTest {
        val forwards = PortForwardType.entries.mapIndexed { i, type -> forward("f$i", type) }
        forwards.forEach { datasource.upsert(it) }

        assertEquals(forwards, datasource.index().first())
        assertEquals(forwards[1], datasource.get(forwards[1].id))
    }

    @Test
    fun `delete and deleteForConnection remove forwards`() = runTest {
        val connectionId = Uuid.random()
        val a = forward("a", PortForwardType.Local, connectionId)
        val b = forward("b", PortForwardType.Remote, connectionId)
        val other = forward("c", PortForwardType.Dynamic)
        listOf(a, b, other).forEach { datasource.upsert(it) }

        datasource.delete(a.id)
        assertNull(datasource.get(a.id))

        datasource.deleteForConnection(connectionId)
        assertEquals(listOf(other), datasource.index().first())
    }
}
