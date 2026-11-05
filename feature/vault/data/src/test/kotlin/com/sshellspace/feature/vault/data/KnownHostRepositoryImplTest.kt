package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.KnownHostDatasource
import com.sshellspace.feature.vault.domain.KnownHostExportResult
import com.sshellspace.feature.vault.domain.KnownHostImportResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

private const val KEY_A = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJon33KV0B2RtiEKmnV3D/RO8Ga6xo29KcAngDLgVTsw"
private const val KEY_B = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILX2Se/ZuAlpJO6Yrahk6gzFmpRh6HKgvFKY+UwzR5je"
private const val KEY_C = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAID8BqjVkj3tn5bLGY2vFY1yeAuSBau3BiiU+Buwpock6"

class KnownHostRepositoryImplTest {

    @TempDir
    lateinit var dir: Path

    private class FakeDatasource : KnownHostDatasource {
        val stored = MutableStateFlow(emptyList<String>())
        override fun index(): Flow<List<String>> = stored
        override suspend fun lines() = stored.value
        override suspend fun add(lines: List<String>) { stored.value = (stored.value + lines).distinct() }
        override suspend fun replace(old: String, new: String) { stored.value = stored.value - old + new }
        override suspend fun delete(line: String) { stored.value = stored.value - line }
    }

    private val datasource = FakeDatasource()
    private val repository by lazy { KnownHostRepositoryImpl(datasource, dir.resolve("device_known_hosts")) }

    private fun file(name: String, text: String): String = Files.writeString(dir.resolve(name), text).toString()

    private suspend fun import(text: String) =
        assertIs<KnownHostImportResult.Success>(repository.importToVault(file("incoming", text)))

    @Test
    fun `imports hosts that name themselves`() = runTest {
        val result = import("# mine\na.example.com $KEY_A\n[b.example.com]:2222 $KEY_B\n")

        assertEquals(2, result.added)
        assertEquals(
            listOf("a.example.com", "b.example.com:2222"),
            repository.getVaultHosts().first().flatMap { it.hosts },
        )
    }

    @Test
    fun `a host already here isn't added twice`() = runTest {
        import("a.example.com $KEY_A\n")

        val again = import("a.example.com $KEY_A\n")

        assertEquals(0, again.added)
        assertEquals(1, again.alreadyKnown)
    }

    @Test
    fun `a file can't add a second key for a host already trusted here`() = runTest {
        import("a.example.com,c.example.com $KEY_A\n")

        val result = import("c.example.com $KEY_B\n")

        assertEquals(0, result.added)
        assertEquals(1, result.conflicting)
        assertEquals(listOf("a.example.com,c.example.com $KEY_A"), datasource.stored.value)
    }

    @Test
    fun `a file contradicting itself keeps the first key`() = runTest {
        val result = import("a.example.com $KEY_A\na.example.com $KEY_B\n")

        assertEquals(1, result.added)
        assertEquals(1, result.conflicting)
    }

    @Test
    fun `wildcards and certificate authorities are skipped and counted`() = runTest {
        val result = import("* $KEY_A\n*.example.com $KEY_B\n@cert-authority a.example.com $KEY_C\nb.example.com $KEY_C\n")

        assertEquals(1, result.added)
        assertEquals(3, result.notAllowed)
        assertEquals(listOf("b.example.com $KEY_C"), datasource.stored.value)
    }

    @Test
    fun `lines that can't be read are counted`() = runTest {
        val result = import("nonsense\nalso nonsense here\na.example.com $KEY_A\n")

        assertEquals(1, result.added)
        assertEquals(2, result.unreadable)
    }

    @Test
    fun `what can't be read as a file fails with a message`() = runTest {
        assertIs<KnownHostImportResult.Error>(repository.importToVault(dir.resolve("missing").toString()))
        assertIs<KnownHostImportResult.Error>(repository.importToVault(dir.toString()))
        val huge = Files.write(dir.resolve("huge"), ByteArray(8 * 1024 * 1024 + 1)).toString()
        assertIs<KnownHostImportResult.Error>(repository.importToVault(huge))
    }

    @Test
    fun `an export imports again into another vault`() = runTest {
        import("a.example.com $KEY_A\n[b.example.com]:2222 $KEY_B\n@revoked c.example.com $KEY_C\n")
        val target = dir.resolve("exported")

        assertEquals(KnownHostExportResult.Success(3), repository.exportVault(target.toString()))

        val otherVault = FakeDatasource()
        val result = KnownHostRepositoryImpl(otherVault, dir.resolve("none")).importToVault(target.toString())
        assertEquals(3, assertIs<KnownHostImportResult.Success>(result).added)
        assertEquals(datasource.stored.value.toSet(), otherVault.stored.value.toSet())
    }

    @Test
    fun `an export doesn't pass on what this list wouldn't trust`() = runTest {
        // How a crafted vault file would hand a wildcard on to whoever imports its export.
        datasource.stored.value = listOf("* $KEY_A", "a.example.com $KEY_B")
        val target = dir.resolve("exported")

        assertEquals(KnownHostExportResult.Success(1), repository.exportVault(target.toString()))

        assertEquals("a.example.com $KEY_B\n", Files.readString(target))
    }

    @Test
    fun `lines this list wouldn't trust aren't listed as if they were`() = runTest {
        datasource.stored.value = listOf("* $KEY_A", "a.example.com $KEY_B")

        assertEquals(listOf("a.example.com"), repository.getVaultHosts().first().flatMap { it.hosts })
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `an export is private to this user`() = runTest {
        import("a.example.com $KEY_A\n")
        val target = dir.resolve("exported")

        repository.exportVault(target.toString())

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)))
    }

    @Test
    fun `an export to a place that can't be written fails with a message`() = runTest {
        assertIs<KnownHostExportResult.Error>(repository.exportVault(dir.resolve("no/such/folder/file").toString()))
    }

    @Test
    fun `removing takes a host off the list`() = runTest {
        import("a.example.com $KEY_A\n")

        repository.removeFromVault(repository.getVaultHosts().first().single())

        assertEquals(emptyList(), datasource.stored.value)
    }

    @Test
    fun `this device's file lists everything in it, repeats once`() = runTest {
        val device = dir.resolve("device_known_hosts")
        Files.writeString(device, "a.example.com $KEY_A\na.example.com $KEY_A\n* $KEY_B\n@cert-authority *.example.com $KEY_C\n")

        val hosts = repository.getDeviceHosts()

        assertEquals(device.toString(), hosts.path)
        assertNull(hosts.error)
        assertEquals(3, hosts.hosts.size)
    }

    @Test
    fun `no file yet just means no hosts`() = runTest {
        val hosts = repository.getDeviceHosts()

        assertEquals(emptyList(), hosts.hosts)
        assertNull(hosts.error)
    }

    @Test
    fun `a file that can't be read says so`() = runTest {
        Files.createDirectory(dir.resolve("device_known_hosts"))

        assertNotNull(repository.getDeviceHosts().error)
    }
}
