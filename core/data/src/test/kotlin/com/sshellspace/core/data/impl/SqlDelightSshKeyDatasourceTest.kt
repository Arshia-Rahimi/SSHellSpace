package com.sshellspace.core.data.impl

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshKeyType
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `insertDeviceKey returns correct SshKeyInfo`() = runTest {
        val result = datasource.insertDeviceKey(
            name = "my-key",
            algorithm = Algorithm.ED25519,
            path = "/home/user/.ssh/id_ed25519",
            passphrase = null,
        )

        assertEquals("my-key", result.name)
        assertEquals(Algorithm.ED25519, result.algorithm)
        assertEquals(SshKeyType.DEVICE, result.type)
        assertNotNull(result.id.toString())
    }

    @Test
    fun `insertVaultKey returns correct SshKeyInfo`() = runTest {
        val result = datasource.insertVaultKey(
            name = "vault-key",
            algorithm = Algorithm.RSA,
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nfake\n-----END OPENSSH PRIVATE KEY-----",
        )

        assertEquals("vault-key", result.name)
        assertEquals(Algorithm.RSA, result.algorithm)
        assertEquals(SshKeyType.VAULT, result.type)
        assertNotNull(result.id.toString())
    }

    @Test
    fun `index combines device and vault keys`() = runTest {
        val deviceKey = datasource.insertDeviceKey(
            name = "device",
            algorithm = Algorithm.ED25519,
            path = "/path/to/key",
            passphrase = null,
        )
        val vaultKey = datasource.insertVaultKey(
            name = "vault",
            algorithm = Algorithm.RSA,
            privateKey = "private-key-data",
        )

        val result = datasource.index().first()

        assertEquals(2, result.size)
        assertEquals(deviceKey, result[0])
        assertEquals(vaultKey, result[1])
    }

    @Test
    fun `get returns correct key by id`() = runTest {
        val inserted = datasource.insertDeviceKey(
            name = "findme",
            algorithm = Algorithm.ED25519,
            path = "/path",
            passphrase = "secret",
        )

        val found = datasource.get(inserted.id)

        assertEquals(inserted, found)
    }

    @Test
    fun `get returns null for non-existent id`() = runTest {
        val nonExistent = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-000000000000")

        assertNull(datasource.get(nonExistent))
    }

    @Test
    fun `updateName changes name and returns updated key`() = runTest {
        val inserted = datasource.insertDeviceKey(
            name = "old-name",
            algorithm = Algorithm.RSA,
            path = "/path",
            passphrase = null,
        )

        val updated = datasource.updateName(inserted.id, "new-name")

        assertNotNull(updated)
        assertEquals("new-name", updated.name)
        assertEquals(inserted.id, updated.id)
        assertEquals(inserted.algorithm, updated.algorithm)
    }

    @Test
    fun `delete removes key`() = runTest {
        val inserted = datasource.insertVaultKey(
            name = "deleteme",
            algorithm = Algorithm.ED25519,
            privateKey = "key-data",
        )

        datasource.delete(inserted.id)

        assertNull(datasource.get(inserted.id))
        assertEquals(emptyList(), datasource.index().first())
    }
}
