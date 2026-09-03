package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.KeyPair
import com.sshellspace.core.domain.models.PrivateKey
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeySecret
import com.sshellspace.core.domain.models.SshKeyType
import com.sshellspace.feature.vault.domain.CreateSshKeyResult
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.uuid.Uuid
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class SshKeyRepositoryImplTest {

    private val datasource = mockk<SshKeyDatasource>()
    private val service = mockk<SshKeyService>()
    private val repository = SshKeyRepositoryImpl(datasource, service)

    @Test
    fun `getKeys delegates to datasource index`() = runTest {
        val info = listOf(
            SshKeyInfo(
                id = Uuid.random(),
                name = "key",
                algorithm = Algorithm.Ed25519,
                type = SshKeyType.DEVICE,
            )
        )
        every { datasource.index() } returns kotlinx.coroutines.flow.flowOf(info)

        val result = repository.getKeys().first()

        assertEquals(info, result)
    }

    @Test
    fun `validate delegates to service`() {
        val passphrase = "secret"
        every { service.validate("key-data", passphrase) } returns
            SshKeyValidationResult.Success(isEncrypted = true, algorithm = Algorithm.RSA)

        val result = repository.validate("key-data", passphrase)

        assertEquals(SshKeyValidationResult.Success(true, Algorithm.RSA), result)
    }

    @Test
    fun `createVaultKey generates and inserts vault key`() = runTest {
        every { service.generate("comment", null, Algorithm.Ed25519) } returns
            ("private-data" to "public-data")
        coEvery {
            datasource.insert("name", Algorithm.Ed25519, SshKeySecret.Vault("private-data"))
        } returns mockk()

        val result = repository.createVaultKey("name", "comment", Algorithm.Ed25519)

        assertEquals(CreateSshKeyResult.Success, result)
        coVerify {
            service.generate("comment", null, Algorithm.Ed25519)
            datasource.insert("name", Algorithm.Ed25519, SshKeySecret.Vault("private-data"))
        }
    }

    @Test
    fun `createVaultKey returns error when generation throws`() = runTest {
        every { service.generate("comment", null, Algorithm.Ed25519) } throws
            IllegalStateException("boom")

        val result = repository.createVaultKey("name", "comment", Algorithm.Ed25519)

        assertIs<CreateSshKeyResult.Error>(result)
        assertEquals("boom", result.message)
        coVerify { datasource wasNot Called }
    }

    @Test
    fun `createDeviceKey writes files and inserts device key`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        every { service.generate("comment", null, Algorithm.Ed25519) } returns
            ("private-data" to "public-data")
        coEvery {
            datasource.insert(
                eq("name"),
                eq(Algorithm.Ed25519),
                any(),
            )
        } answers {
            val secret = thirdArg<SshKeySecret>()
            assertIs<SshKeySecret.Device>(secret)
            assertEquals(null, secret.passphrase)
            mockk()
        }

        val result = repository.createDeviceKey(
            name = "name",
            comment = "comment",
            fileName = "mykey",
            path = dir.absolutePath,
            algorithm = Algorithm.Ed25519,
        )

        assertEquals(CreateSshKeyResult.Success, result)
        val privateFile = File(dir, "mykey")
        val publicFile = File(dir, "mykey.pub")
        assertTrue(privateFile.exists())
        assertTrue(publicFile.exists())
        assertEquals("private-data", privateFile.readText())
        assertEquals("public-data", publicFile.readText())
    }

    @Test
    fun `createDeviceKey uses default file name when blank`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        every { service.generate("comment", null, Algorithm.Ed25519) } returns
            ("private-data" to "public-data")
        coEvery { datasource.insert(any(), any(), any()) } returns mockk()

        val result = repository.createDeviceKey(
            name = "name",
            comment = "comment",
            fileName = "",
            path = dir.absolutePath,
            algorithm = Algorithm.Ed25519,
        )

        assertEquals(CreateSshKeyResult.Success, result)
        assertTrue(File(dir, "SSHellSpace_name_Ed25519").exists())
    }

    @Test
    fun `createDeviceKey returns error when file already exists`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        val existing = File(dir, "mykey")
        existing.writeText("existing")

        val result = repository.createDeviceKey(
            name = "name",
            comment = "comment",
            fileName = "mykey",
            path = dir.absolutePath,
            algorithm = Algorithm.Ed25519,
        )

        assertIs<CreateSshKeyResult.Error>(result)
        assertTrue(result.message!!.contains("exists"))
        coVerify { datasource wasNot Called }
    }

    @Test
    fun `createDeviceKey returns error when public file already exists`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        val existing = File(dir, "mykey.pub")
        existing.writeText("existing")

        val result = repository.createDeviceKey(
            name = "name",
            comment = "comment",
            fileName = "mykey",
            path = dir.absolutePath,
            algorithm = Algorithm.Ed25519,
        )

        assertIs<CreateSshKeyResult.Error>(result)
        assertTrue(result.message!!.contains("exists"))
        coVerify { datasource wasNot Called }
    }

    @Test
    fun `importDeviceKey returns error when file does not exist`() = runTest {
        val result = repository.importDeviceKey("name", "/nonexistent/path", null)

        assertIs<CreateSshKeyResult.Error>(result)
        assertTrue(result.message!!.contains("does not exist"))
    }

    @Test
    fun `importDeviceKey returns error on invalid passphrase`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        val file = File(dir, "key")
        file.writeText("key-data")
        every { service.validate("key-data", "wrong") } returns
            SshKeyValidationResult.InvalidPassphrase

        val result = repository.importDeviceKey("name", file.absolutePath, "wrong")

        assertIs<CreateSshKeyResult.Error>(result)
        assertEquals("Invalid Passphrase", result.message)
        coVerify { datasource wasNot Called }
    }

    @Test
    fun `importDeviceKey returns error on invalid key format`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        val file = File(dir, "key")
        file.writeText("key-data")
        every { service.validate("key-data", null) } returns
            SshKeyValidationResult.InvalidSshKeyFormat("bad")

        val result = repository.importDeviceKey("name", file.absolutePath, null)

        assertIs<CreateSshKeyResult.Error>(result)
        assertEquals("Key format is not correct", result.message)
        coVerify { datasource wasNot Called }
    }

    @Test
    fun `importDeviceKey inserts device key on success`() = runTest {
        val dir = Files.createTempDirectory("sshkey-test").toFile()
        val file = File(dir, "key")
        file.writeText("key-data")
        every { service.validate("key-data", null) } returns
            SshKeyValidationResult.Success(isEncrypted = false, algorithm = Algorithm.Ed25519)
        coEvery {
            datasource.insert(
                eq("name"),
                eq(Algorithm.Ed25519),
                eq(SshKeySecret.Device(file.absolutePath, null)),
            )
        } returns mockk()

        val result = repository.importDeviceKey("name", file.absolutePath, null)

        assertEquals(CreateSshKeyResult.Success, result)
        coVerify {
            datasource.insert("name", Algorithm.Ed25519, SshKeySecret.Device(file.absolutePath, null))
        }
    }

    @Test
    fun `updateName delegates to datasource`() = runTest {
        val id = Uuid.random()
        coEvery { datasource.updateName(id, "new-name") } returns mockk()

        repository.updateName(id, "new-name")

        coVerify { datasource.updateName(id, "new-name") }
    }

    @Test
    fun `delete delegates to datasource`() = runTest {
        val id = Uuid.random()
        coEvery { datasource.delete(id) } returns Unit

        repository.delete(id)

        coVerify { datasource.delete(id) }
    }
}
