package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.PortForwardDatasource
import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.domain.models.LOCALHOST
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.feature.vault.domain.PortForwardDraft
import com.sshellspace.feature.vault.domain.SavePortForwardResult
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

class PortForwardRepositoryImplTest {

    private val forwardDatasource = mockk<PortForwardDatasource>(relaxUnitFun = true)
    private val connectionDatasource = mockk<SshConnectionDatasource>()
    private val repository = PortForwardRepositoryImpl(forwardDatasource, connectionDatasource)

    private val connection = SshConnection(
        id = Uuid.random(),
        name = "server",
        host = "example.com",
        username = "root",
        auth = SshConnectionAuth.Agent,
    )

    private fun draft(
        name: String = "postgres",
        type: PortForwardType = PortForwardType.Local,
        connectionId: Uuid = connection.id,
        destinationHost: String = " db.internal ",
        destinationPort: Int = 5432,
    ) = PortForwardDraft(
        name = name,
        connectionId = connectionId,
        type = type,
        bindHost = "",
        bindPort = 15432,
        destinationHost = destinationHost,
        destinationPort = destinationPort,
    )

    private fun given(vararg forwards: PortForward) {
        every { forwardDatasource.index() } returns flowOf(forwards.toList())
        coEvery { connectionDatasource.get(any()) } returns null
        coEvery { connectionDatasource.get(connection.id) } returns connection
    }

    @Test
    fun `save trims fields and binds to localhost by default`() = runTest {
        given()

        val forward = assertIs<SavePortForwardResult.Success>(repository.save(null, draft())).forward

        assertEquals(LOCALHOST, forward.bindHost)
        assertEquals("db.internal", forward.destinationHost)
        coVerify { forwardDatasource.upsert(forward) }
    }

    @Test
    fun `save ignores the destination of a dynamic forward`() = runTest {
        given()

        val result = repository.save(null, draft(type = PortForwardType.Dynamic, destinationHost = "", destinationPort = 0))

        assertEquals(0, assertIs<SavePortForwardResult.Success>(result).forward.destinationPort)
    }

    @Test
    fun `save rejects a missing destination, a deleted connection and a duplicate name`() = runTest {
        val existing = PortForward(
            id = Uuid.random(),
            name = "Postgres",
            connectionId = connection.id,
            type = PortForwardType.Local,
            bindPort = 1,
            destinationPort = 1,
        )
        given(existing)

        assertIs<SavePortForwardResult.Error>(repository.save(null, draft(name = "other", destinationHost = " ")))
        assertIs<SavePortForwardResult.Error>(repository.save(null, draft(name = "other", destinationPort = 0)))
        assertIs<SavePortForwardResult.Error>(repository.save(null, draft(name = "other", connectionId = Uuid.random())))
        assertIs<SavePortForwardResult.Error>(repository.save(null, draft(name = "postgres")))
        assertIs<SavePortForwardResult.Success>(repository.save(existing.id, draft(name = "postgres")))
    }
}
