package com.sshellspace.feature.vault.data

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.feature.vault.domain.SaveSshConnectionResult
import com.sshellspace.feature.vault.domain.SshConnectionDraft
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class SshConfigImporterImplTest {

    private class FakeConnections : SshConnectionRepository {
        val saved = MutableStateFlow(emptyList<SshConnection>())
        override fun getConnections() = saved
        override suspend fun get(id: Uuid) = saved.value.firstOrNull { it.id == id }
        override suspend fun delete(id: Uuid) = Unit
        override suspend fun save(id: Uuid?, draft: SshConnectionDraft): SaveSshConnectionResult {
            val connection = SshConnection(
                Uuid.random(),
                draft.name,
                draft.host,
                draft.port,
                draft.username,
                draft.auth,
                draft.options,
            )
            saved.value += connection
            return SaveSshConnectionResult.Success(connection)
        }
    }

    private val home = Files.createTempDirectory("ssh-config-test")
    private val ssh = home.resolve(".ssh").createDirectories()
    private val connections = FakeConnections()

    private fun importer(hasAgent: Boolean = false) =
        SshConfigImporterImpl(connections, ssh, defaultUser = "me", hasAgent = { hasAgent })

    @Test
    fun `no config file means nothing to import`() = runTest {
        assertEquals(emptyList(), importer().preview())
    }

    @Test
    fun `includes resolve against the ssh folder, with wildcards`() = runTest {
        ssh.resolve("config").writeText("Include conf.d/*.conf\nHost main")
        ssh.resolve("conf.d").createDirectories()
        ssh.resolve("conf.d/a.conf").writeText("Host from-a")
        ssh.resolve("conf.d/skip.txt").writeText("Host skipped")

        assertEquals(listOf("from-a", "main"), importer().preview().map { it.alias })
    }

    @Test
    fun `a host already saved is marked`() = runTest {
        ssh.resolve("config").writeText("Host web\nHost db")
        importer().import(importer().preview().filter { it.alias == "web" })

        assertEquals(listOf(true, false), importer().preview().map { it.alreadySaved })
    }

    @Test
    fun `jump hosts are saved first and linked, whatever the file order`() = runTest {
        ssh.resolve("config").writeText("Host target\n  ProxyJump bastion\nHost bastion")

        val result = importer().import(importer().preview())

        assertEquals(2, result.imported)
        assertEquals(emptyList(), result.problems)
        val byName = connections.saved.value.associateBy { it.name }
        assertEquals(byName.getValue("bastion").id, byName.getValue("target").options.jumpHostId)
    }

    @Test
    fun `a jump host that wasn't imported is reported`() = runTest {
        ssh.resolve("config").writeText("Host target\n  ProxyJump elsewhere")

        val result = importer().import(importer().preview())

        assertEquals(1, result.imported)
        assertTrue(result.problems.single().contains("elsewhere"))
    }

    @Test
    fun `a jump loop in the file doesn't hang`() = runTest {
        ssh.resolve("config").writeText("Host a\n  ProxyJump b\nHost b\n  ProxyJump a")

        assertEquals(2, importer().import(importer().preview()).imported)
    }

    @Test
    fun `the last of several hops is the one connected through`() = runTest {
        ssh.resolve("config").writeText("Host t\n  ProxyJump a,user@b:2222\nHost a\nHost b")

        importer().import(importer().preview())

        val byName = connections.saved.value.associateBy { it.name }
        assertEquals(byName.getValue("b").id, byName.getValue("t").options.jumpHostId)
    }

    @Test
    fun `auth comes from the key, then the agent, then asking`() = runTest {
        ssh.resolve("config").writeText("Host keyed\n  IdentityFile ~/.ssh/k\nHost plain")

        importer(hasAgent = true).import(importer().preview())
        val withAgent = connections.saved.value.associateBy { it.name }
        assertIs<SshConnectionAuth.KeyFile>(withAgent.getValue("keyed").auth)
        assertIs<SshConnectionAuth.Agent>(withAgent.getValue("plain").auth)

        connections.saved.value = emptyList()
        importer(hasAgent = false).import(importer().preview())
        assertIs<SshConnectionAuth.Password>(connections.saved.value.single { it.name == "plain" }.auth)
    }

    @Test
    fun `no secrets or user mean sensible defaults, tagged for finding later`() = runTest {
        ssh.resolve("config").writeText("Host box")

        importer().import(importer().preview())

        val box = connections.saved.value.single()
        assertEquals("me", box.username)
        assertEquals(listOf("ssh-config"), box.options.tags)
    }
}
