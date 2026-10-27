package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshKeySecret
import com.sshellspace.core.domain.models.SshKeyType
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightSshKeyDatasourceTest {

    private val db = Database(
        driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = Database.Schema,
            properties = Properties(),
        ),
        device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
        vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
    )

    private val datasource = SqlDelightSshKeyDatasource(db)

    @Test
    fun `index returns empty list initially`() = runTest {
        assertEquals(emptyList(), datasource.index().first())
    }

    @Test
    fun `insert returns correct SshKeyInfo`() = runTest {
        val result = datasource.insert(
            name = "my-key",
            algorithm = Algorithm.Ed25519,
            secret = SshKeySecret.Vault("alkfjadf"),
        )

        assertEquals("my-key", result.name)
        assertEquals(Algorithm.Ed25519, result.algorithm)
        assertEquals(SshKeyType.VAULT, result.type)
        assertNotNull(result.id.toString())
    }

    @Test
    fun `index combines device and vault keys`() = runTest {
        val deviceKey = datasource.insert(
            name = "device",
            algorithm = Algorithm.Ed25519,
            secret = SshKeySecret.Device(path = "/path/to/key", passphrase = null),
        )
        val vaultKey = datasource.insert(
            name = "vault",
            algorithm = Algorithm.RSA,
            secret = SshKeySecret.Vault("private-key-data"),
        )

        val result = datasource.index().first()

        assertEquals(2, result.size)
        assertEquals(deviceKey, result[0])
        assertEquals(SshKeyType.DEVICE, result[0].type)
        assertEquals(vaultKey, result[1])
        assertEquals(SshKeyType.VAULT, result[1].type)
    }

    @Test
    fun `get returns correct key by id`() = runTest {
        val inserted = datasource.insert(
            name = "findme",
            algorithm = Algorithm.Ed25519,
            secret = SshKeySecret.Device(path = "/path", passphrase = "secret"),
        )

        val found = datasource.get(inserted.id)

        assertEquals(inserted, found)
    }

    @Test
    fun `get returns null for non-existent id`() = runTest {
        val nonExistent = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-000000000000")

        assertEquals(null, datasource.get(nonExistent))
    }

    @Test
    fun `updateName changes name and returns updated key`() = runTest {
        val inserted = datasource.insert(
            name = "old-name",
            algorithm = Algorithm.RSA,
            secret = SshKeySecret.Device(path = "/path", passphrase = null),
        )

        val updated = datasource.updateName(inserted.id, "new-name")

        assertNotNull(updated)
        assertEquals("new-name", updated.name)
        assertEquals(inserted.id, updated.id)
        assertEquals(inserted.algorithm, updated.algorithm)
    }

    @Test
    fun `delete removes key`() = runTest {
        val inserted = datasource.insert(
            name = "deleteme",
            algorithm = Algorithm.Ed25519,
            secret = SshKeySecret.Vault("key-data"),
        )

        datasource.delete(inserted.id)

        assertEquals(null, datasource.get(inserted.id))
        assertEquals(emptyList(), datasource.index().first())
    }

    @Test
    fun `getSecret returns device secret`() = runTest {
        val inserted = datasource.insert(
            name = "device",
            algorithm = Algorithm.Ed25519,
            secret = SshKeySecret.Device(path = "/path", passphrase = "secret"),
        )

        assertEquals(SshKeySecret.Device(path = "/path", passphrase = "secret"), datasource.getSecret(inserted.id))
    }

    @Test
    fun `getSecret returns vault secret`() = runTest {
        val inserted = datasource.insert(
            name = "vault",
            algorithm = Algorithm.RSA,
            secret = SshKeySecret.Vault("private-key-data"),
        )

        assertEquals(SshKeySecret.Vault("private-key-data"), datasource.getSecret(inserted.id))
    }

    @Test
    fun `getSecret returns null for non-existent id`() = runTest {
        val nonExistent = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-000000000000")

        assertEquals(null, datasource.getSecret(nonExistent))
    }
}
