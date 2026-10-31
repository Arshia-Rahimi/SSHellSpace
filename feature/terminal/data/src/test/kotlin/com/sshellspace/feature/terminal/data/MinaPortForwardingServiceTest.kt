package com.sshellspace.feature.terminal.data

import com.sshellspace.core.data.PortForwardDatasource
import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import com.sshellspace.feature.terminal.data.ssh.agent.SshAgentLocator
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import io.mockk.mockk
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider

/** Runs real forwards through an in-process MINA SSH server to a local echo server. */
class MinaPortForwardingServiceTest {

    private val tempDir: Path = Files.createTempDirectory("sshellspace-forward-test")

    private val server = SshServer.setUpDefaultServer().apply {
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(tempDir.resolve("host.key"))
        passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == "alice" && password == "secret" }
        forwardingFilter = AcceptAllForwardingFilter.INSTANCE
        start()
    }

    private val echo = EchoServer()
    private val client = createSshClient(tempDir.resolve("known_hosts"), SshAgentLocator { null })
    private val connection = SshConnection(
        id = Uuid.random(),
        name = "test server",
        host = "127.0.0.1",
        port = server.port,
        username = "alice",
        auth = SshConnectionAuth.Password("secret"),
        options = SshConnectionOptions(connectTimeoutSeconds = 5),
    )
    private val connectionService = CountingConnections(
        MinaSshConnectionService(
            client,
            SingleConnection(connection),
            mockk<SshKeyDatasource>(),
            mockk<SshKeyService>(),
            SshAgentLocator { null },
        ),
    )
    private val savedForwards = FakeForwards()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val service = MinaPortForwardingService(connectionService, savedForwards, scope)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        client.stop()
        server.stop(true)
        echo.close()
        tempDir.toFile().deleteRecursively()
    }

    private fun forward(
        type: PortForwardType,
        bindPort: Int = freePort(),
        destinationPort: Int = echo.port,
    ) = PortForward(
        id = Uuid.random(),
        name = "$type $bindPort",
        connectionId = connection.id,
        type = type,
        bindPort = bindPort,
        destinationPort = destinationPort,
    ).also(savedForwards::add)

    private fun start(forward: PortForward, prompter: SshPrompter = AcceptingPrompter()) = runBlocking {
        withTimeout(30_000) { service.start(forward, prompter) }
        service.states.value[forward.id]
    }

    private fun awaitState(id: Uuid, predicate: (PortForwardState?) -> Boolean) = runBlocking {
        withTimeout(10_000) { service.states.first { predicate(it[id]) }[id] }
    }

    @Test
    fun `local forward carries traffic to the destination`() {
        val forward = forward(PortForwardType.Local)

        val state = assertIs<PortForwardState.Active>(start(forward))

        assertEquals("127.0.0.1:${forward.bindPort}", state.boundAddress)
        assertEquals("ping", roundTrip(Socket("127.0.0.1", forward.bindPort), "ping"))
    }

    @Test
    fun `remote forward listens on the server and reaches this device`() {
        val forward = forward(PortForwardType.Remote)

        assertIs<PortForwardState.Active>(start(forward))

        assertEquals("pong", roundTrip(Socket("127.0.0.1", forward.bindPort), "pong"))
    }

    @Test
    fun `dynamic forward is a SOCKS proxy`() {
        val forward = forward(PortForwardType.Dynamic)

        assertIs<PortForwardState.Active>(start(forward))

        val socket = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", forward.bindPort)))
        socket.connect(InetSocketAddress("127.0.0.1", echo.port), 5_000)
        assertEquals("über", roundTrip(socket, "über"))
    }

    @Test
    fun `forwards on one connection share a session until the last one stops`() {
        val first = forward(PortForwardType.Local)
        val second = forward(PortForwardType.Dynamic)

        assertIs<PortForwardState.Active>(start(first))
        assertIs<PortForwardState.Active>(start(second))
        assertEquals(1, connectionService.connects.get())
        assertEquals(1, server.activeSessions.size)

        service.stop(first.id)
        assertNull(service.states.value[first.id])
        assertEquals("still up", roundTrip(socksTo(second), "still up"))

        service.stop(second.id)
        runBlocking { withTimeout(5_000) { while (server.activeSessions.isNotEmpty()) delay(50) } }
    }

    @Test
    fun `a port that is already taken fails with a clear message`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { taken ->
            val failed = assertIs<PortForwardState.Failed>(start(forward(PortForwardType.Local, bindPort = taken.localPort)))
            assertTrue(failed.message.contains("already in use"), failed.message)
        }
    }

    @Test
    fun `rejecting the host key leaves the forward stopped`() {
        val forward = forward(PortForwardType.Local)

        assertNull(start(forward, AcceptingPrompter(acceptHostKeys = false)))
        assertEquals(0, server.activeSessions.size)
    }

    @Test
    fun `a dropped connection marks its forwards failed`() {
        val forward = forward(PortForwardType.Local)
        assertIs<PortForwardState.Active>(start(forward))

        server.activeSessions.forEach { it.close(true) }

        val failed = assertIs<PortForwardState.Failed>(awaitState(forward.id) { it is PortForwardState.Failed })
        assertEquals("The connection to test server closed.", failed.message)
    }

    @Test
    fun `deleting a running forward stops it`() {
        val forward = forward(PortForwardType.Local)
        assertIs<PortForwardState.Active>(start(forward))

        savedForwards.remove(forward.id)

        awaitState(forward.id) { it == null }
    }

    private fun socksTo(forward: PortForward) =
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", forward.bindPort))).apply {
            connect(InetSocketAddress("127.0.0.1", echo.port), 5_000)
        }

    private fun roundTrip(socket: Socket, message: String): String = socket.use {
        it.soTimeout = 5_000
        val bytes = message.toByteArray()
        it.getOutputStream().apply { write(bytes); flush() }
        String(it.getInputStream().readNBytes(bytes.size))
    }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private class EchoServer : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int = socket.localPort

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { client.use { it.getInputStream().transferTo(it.getOutputStream()) } }
                }
            }
        }

        override fun close() = socket.close()
    }

    private class AcceptingPrompter(private val acceptHostKeys: Boolean = true) : SshPrompter {
        override suspend fun confirmHostKey(prompt: HostKeyPrompt) = acceptHostKeys
        override suspend fun askSecret(prompt: SecretPrompt): String? = null
        override suspend fun answerChallenge(challenge: AuthChallenge): List<String>? = null
    }

    private class CountingConnections(private val delegate: SshConnectionService) : SshConnectionService {
        val connects = AtomicInteger()
        override suspend fun connect(connectionId: Uuid, prompter: SshPrompter) =
            delegate.connect(connectionId, prompter).also { connects.incrementAndGet() }
    }

    private class SingleConnection(private val connection: SshConnection) : SshConnectionDatasource {
        override fun index(): Flow<List<SshConnection>> = flowOf(listOf(connection))
        override suspend fun get(id: Uuid) = connection.takeIf { it.id == id }
        override suspend fun upsert(connection: SshConnection) = Unit
        override suspend fun delete(id: Uuid) = Unit
    }

    private class FakeForwards : PortForwardDatasource {
        private val forwards = MutableStateFlow<List<PortForward>>(emptyList())
        fun add(forward: PortForward) = forwards.update { it + forward }
        fun remove(id: Uuid) = forwards.update { list -> list.filter { it.id != id } }
        override fun index(): Flow<List<PortForward>> = forwards
        override suspend fun get(id: Uuid) = forwards.value.firstOrNull { it.id == id }
        override suspend fun upsert(forward: PortForward) = add(forward)
        override suspend fun delete(id: Uuid) = remove(id)
        override suspend fun deleteForConnection(connectionId: Uuid) =
            forwards.update { list -> list.filter { it.connectionId != connectionId } }
    }
}
