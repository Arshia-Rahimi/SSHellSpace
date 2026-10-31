package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import com.sshellspace.feature.vault.domain.SaveSshConnectionResult
import com.sshellspace.feature.vault.domain.SshConnectionDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class SshConnectionRepositoryImplTest {

    private val connectionDatasource = mockk<SshConnectionDatasource>(relaxUnitFun = true)
    private val keyDatasource = mockk<SshKeyDatasource>()
    private val repository = SshConnectionRepositoryImpl(connectionDatasource, keyDatasource)

    private fun draft(
        name: String = "server",
        auth: SshConnectionAuth = SshConnectionAuth.Agent,
        jumpHostId: Uuid? = null,
    ) = SshConnectionDraft(
        name = name,
        host = " example.com ",
        port = 22,
        username = "root",
        auth = auth,
        options = SshConnectionOptions(jumpHostId = jumpHostId),
    )

    private fun stored(name: String, jumpHostId: Uuid? = null) = SshConnection(
        id = Uuid.random(),
        name = name,
        host = "h",
        username = "u",
        auth = SshConnectionAuth.Agent,
        options = SshConnectionOptions(jumpHostId = jumpHostId),
    )

    private fun givenStored(vararg connections: SshConnection) {
        every { connectionDatasource.index() } returns flowOf(connections.toList())
    }

    @Test
    fun `save creates a new connection with trimmed fields`() = runTest {
        givenStored()

        val result = assertIs<SaveSshConnectionResult.Success>(repository.save(null, draft()))

        assertEquals("example.com", result.connection.host)
        coVerify { connectionDatasource.upsert(result.connection) }
    }

    @Test
    fun `save rejects a duplicate name`() = runTest {
        givenStored(stored("Server"))

        assertIs<SaveSshConnectionResult.Error>(repository.save(null, draft(name = "server")))
    }

    @Test
    fun `save rejects a stored key that no longer exists`() = runTest {
        givenStored()
        val keyId = Uuid.random()
        coEvery { keyDatasource.get(keyId) } returns null

        assertIs<SaveSshConnectionResult.Error>(repository.save(null, draft(auth = SshConnectionAuth.StoredKey(keyId))))
    }

    @Test
    fun `save rejects a jump host chain that loops back`() = runTest {
        val a = stored("a")
        val b = stored("b", jumpHostId = a.id)
        givenStored(a, b)

        // a -> b -> a
        val result = repository.save(a.id, draft(name = "a", jumpHostId = b.id))

        assertEquals("Jump hosts can't form a loop", assertIs<SaveSshConnectionResult.Error>(result).message)
    }

    @Test
    fun `delete clears the deleted connection as a jump host`() = runTest {
        val bastion = stored("bastion")
        val inner = stored("inner", jumpHostId = bastion.id)
        givenStored(bastion, inner)

        repository.delete(bastion.id)

        coVerify { connectionDatasource.upsert(inner.copy(options = inner.options.copy(jumpHostId = null))) }
        coVerify { connectionDatasource.delete(bastion.id) }
    }
}
