package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightSshConnectionDatasourceTest {

    private val datasource = SqlDelightSshConnectionDatasource(
        db = Database(
            driver = JdbcSqliteDriver(
                url = JdbcSqliteDriver.IN_MEMORY,
                schema = Database.Schema,
                properties = Properties(),
            ),
            device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
            vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
        ),
    )

    private fun connection(name: String, auth: SshConnectionAuth) = SshConnection(
        id = Uuid.random(),
        name = name,
        host = "example.com",
        port = 2222,
        username = "root",
        auth = auth,
        options = SshConnectionOptions(
            jumpHostId = Uuid.random(),
            keepAliveIntervalSeconds = 30,
            compression = true,
            startupCommand = "tmux attach",
        ),
    )

    @Test
    fun `every auth method survives a round trip`() = runTest {
        val connections = listOf(
            connection("a", SshConnectionAuth.Password("secret")),
            connection("b", SshConnectionAuth.Password(null)),
            connection("c", SshConnectionAuth.StoredKey(Uuid.random())),
            connection("d", SshConnectionAuth.KeyFile("/home/me/.ssh/id_ed25519", "pass")),
            connection("e", SshConnectionAuth.Agent),
            connection("f", SshConnectionAuth.KeyboardInteractive("pw")),
        )
        connections.forEach { datasource.upsert(it) }

        assertEquals(connections, datasource.index().first())
        assertEquals(connections[3], datasource.get(connections[3].id))
    }

    @Test
    fun `upsert replaces an existing connection and delete removes it`() = runTest {
        val original = connection("server", SshConnectionAuth.Agent)
        datasource.upsert(original)

        val updated = original.copy(host = "other.example.com", auth = SshConnectionAuth.Password("x"))
        datasource.upsert(updated)
        assertEquals(listOf(updated), datasource.index().first())

        datasource.delete(original.id)
        assertNull(datasource.get(original.id))
    }
}
