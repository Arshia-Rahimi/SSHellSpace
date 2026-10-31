package com.sshellspace.feature.terminal.data

import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeySecret
import com.sshellspace.core.domain.models.SshKeyType
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.data.ssh.agent.SshAgentLocator
import io.mockk.mockk
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPair
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.session.ServerSession
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Runs real connections against an in-process MINA SSH server. */
class MinaSshConnectionServiceTest {

    private val tempDir: Path = Files.createTempDirectory("sshellspace-ssh-test")
    private val knownHosts: Path = tempDir.resolve("ssh/known_hosts")
    private val clientKey: KeyPair = KeyUtils.generateKeyPair("ssh-ed25519", 256)

    private val server = SshServer.setUpDefaultServer().apply {
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(tempDir.resolve("host.key"))
        passwordAuthenticator = org.apache.sshd.server.auth.password.PasswordAuthenticator { user, password, _ ->
            user == "alice" && password == "secret"
        }
        publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { user, key, _ ->
            user == "alice" && KeyUtils.compareKeys(key, clientKey.public)
        }
        keyboardInteractiveAuthenticator = OtpAuthenticator
        forwardingFilter = AcceptAllForwardingFilter.INSTANCE
        setShellFactory { EchoShell() }
        start()
    }

    private var agentSocket: String? = null
    private val agentLocator = SshAgentLocator { agentSocket }
    private val client = createSshClient(knownHosts, agentLocator)
    private val connections = FakeConnections()
    private val keys = FakeKeys()
    private val service = MinaSshConnectionService(client, connections, keys, ParsingKeyService, agentLocator)

    @AfterTest
    fun tearDown() {
        client.stop()
        server.stop(true)
        tempDir.toFile().deleteRecursively()
    }

    private fun connection(
        auth: SshConnectionAuth,
        host: String = "127.0.0.1",
        username: String = "alice",
        port: Int = server.port,
        jumpHostId: Uuid? = null,
    ) = SshConnection(
        id = Uuid.random(),
        name = "$host-${auth::class.simpleName}",
        host = host,
        port = port,
        username = username,
        auth = auth,
        options = SshConnectionOptions(jumpHostId = jumpHostId, connectTimeoutSeconds = 5),
    ).also(connections::add)

    private fun connect(connection: SshConnection, prompter: FakePrompter) = runBlocking {
        withTimeout(30_000) { service.connect(connection.id, prompter) }
    }

    @Test
    fun `password auth asks about an unknown host key once and records it`() {
        val target = connection(SshConnectionAuth.Password("secret"))

        val first = FakePrompter()
        assertIs<SshConnectResult.Success>(connect(target, first)).session.close()
        assertEquals(1, first.hostKeys.size)
        assertNull(first.hostKeys.single().expectedFingerprint)
        assertTrue(Files.readString(knownHosts).contains("[127.0.0.1]:${server.port}"))

        val second = FakePrompter()
        assertIs<SshConnectResult.Success>(connect(target, second)).session.close()
        assertEquals(emptyList(), second.hostKeys)
    }

    @Test
    fun `rejecting the host key cancels and leaves known_hosts alone`() {
        val target = connection(SshConnectionAuth.Password("secret"))

        val result = connect(target, FakePrompter(acceptHostKeys = false))

        assertIs<SshConnectResult.Cancelled>(result)
        assertTrue(!Files.exists(knownHosts) || Files.readString(knownHosts).isBlank())
    }

    @Test
    fun `a changed host key is reported with the expected fingerprint`() {
        val other = KeyUtils.generateKeyPair("ssh-ed25519", 256).public
        Files.createDirectories(knownHosts.parent)
        Files.writeString(knownHosts, "[127.0.0.1]:${server.port} ${PublicKeyEntry.toString(other)}\n")
        val target = connection(SshConnectionAuth.Password("secret"))
        val prompter = FakePrompter(acceptHostKeys = false)

        assertIs<SshConnectResult.Cancelled>(connect(target, prompter))

        assertEquals(KeyUtils.getFingerPrint(other), prompter.hostKeys.single().expectedFingerprint)
    }

    @Test
    fun `a missing password is asked for and asked again after a wrong answer`() {
        val target = connection(SshConnectionAuth.Password(null))
        val prompter = FakePrompter(secrets = ArrayDeque(listOf("wrong", "secret")))

        assertIs<SshConnectResult.Success>(connect(target, prompter)).session.close()

        assertEquals(2, prompter.secrets.size)
        assertNull(prompter.secrets[0].error)
        assertNotNull(prompter.secrets[1].error)
    }

    @Test
    fun `cancelling the password prompt cancels the connection`() {
        val target = connection(SshConnectionAuth.Password(null))

        assertIs<SshConnectResult.Cancelled>(connect(target, FakePrompter(secrets = ArrayDeque())))
    }

    @Test
    fun `stored device key with an unsaved passphrase asks for it`() {
        val keyFile = tempDir.resolve("id_ed25519")
        Files.writeString(keyFile, clientKey.encrypted("pass phrase"))
        val keyId = keys.add(SshKeySecret.Device(keyFile.toString(), passphrase = null))
        val target = connection(SshConnectionAuth.StoredKey(keyId))
        val prompter = FakePrompter(secrets = ArrayDeque(listOf("pass phrase")))

        assertIs<SshConnectResult.Success>(connect(target, prompter)).session.close()

        assertEquals("Key passphrase", prompter.secrets.single().title)
    }

    @Test
    fun `key file auth fails clearly when the file is missing`() {
        val target = connection(SshConnectionAuth.KeyFile(tempDir.resolve("nope").toString()))

        val result = assertIs<SshConnectResult.Failed>(connect(target, FakePrompter()))

        assertTrue(result.message.startsWith("Key file not found"))
    }

    @Test
    fun `keyboard-interactive answers the server's challenge`() {
        val target = connection(SshConnectionAuth.KeyboardInteractive(), username = "otp-user")
        val prompter = FakePrompter(challengeAnswers = listOf("123456"))

        assertIs<SshConnectResult.Success>(connect(target, prompter)).session.close()

        assertEquals("Verification code: ", prompter.challenges.single().prompts.single().text)
    }

    @Test
    fun `wrong credentials fail with an authentication message`() {
        val target = connection(SshConnectionAuth.KeyboardInteractive(), username = "otp-user")

        val result = connect(target, FakePrompter(challengeAnswers = listOf("000000")))

        assertTrue(assertIs<SshConnectResult.Failed>(result).message.startsWith("Authentication failed"))
    }

    @Test
    fun `jump host tunnels to the target and checks the target under its own name`() {
        val jump = connection(SshConnectionAuth.Password("secret"), host = "localhost")
        val target = connection(SshConnectionAuth.Password("secret"), jumpHostId = jump.id)
        val prompter = FakePrompter()

        val session = assertIs<SshConnectResult.Success>(connect(target, prompter)).session
        session.close()

        assertEquals(listOf("localhost", "127.0.0.1"), prompter.hostKeys.map { it.host })
        val recorded = Files.readString(knownHosts)
        assertTrue(recorded.contains("[localhost]:${server.port}"))
        assertTrue(recorded.contains("[127.0.0.1]:${server.port}"))
    }

    @Test
    fun `agent auth signs with a key held by a real ssh-agent`() {
        val sshAgent = findExecutable("ssh-agent")
        val sshAdd = findExecutable("ssh-add")
        assumeTrue(sshAgent != null && sshAdd != null, "OpenSSH agent tools aren't installed")

        val socket = tempDir.resolve("agent.sock")
        val agent = ProcessBuilder(sshAgent.toString(), "-D", "-a", socket.toString()).redirectErrorStream(true).start()
        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (!Files.exists(socket) && System.currentTimeMillis() < deadline) Thread.sleep(50)

            val keyFile = tempDir.resolve("agent_key")
            Files.writeString(keyFile, clientKey.unencrypted())
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"))
            val added = ProcessBuilder(sshAdd.toString(), keyFile.toString())
                .apply { environment()["SSH_AUTH_SOCK"] = socket.toString() }
                .redirectErrorStream(true)
                .start()
            assertEquals(0, added.waitFor(), added.inputStream.bufferedReader().readText())

            agentSocket = socket.toString()
            val target = connection(SshConnectionAuth.Agent)

            assertIs<SshConnectResult.Success>(connect(target, FakePrompter())).session.close()
        } finally {
            agent.destroy()
        }
    }

    @Test
    fun `agent auth fails clearly when no agent is running`() {
        agentSocket = null
        val target = connection(SshConnectionAuth.Agent)

        val result = assertIs<SshConnectResult.Failed>(connect(target, FakePrompter()))

        assertTrue(result.message.startsWith("No SSH agent found"))
    }

    @Test
    fun `a closed port fails with a readable message`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val target = connection(SshConnectionAuth.Password("secret"), port = closedPort)

        val result = assertIs<SshConnectResult.Failed>(connect(target, FakePrompter()))

        assertEquals("127.0.0.1:$closedPort refused the connection.", result.message)
    }

    @Test
    fun `the shell round-trips text including multi-byte characters`() = runBlocking {
        val target = connection(SshConnectionAuth.Password("secret"))
        val session = assertIs<SshConnectResult.Success>(connect(target, FakePrompter())).session
        val shell = session.openShell(80, 24)

        shell.write("héllo ✓\n")
        val output = StringBuilder()
        withTimeout(10_000) {
            while (!output.contains("héllo ✓")) output.append(shell.read() ?: break)
        }

        assertTrue(output.contains("héllo ✓"))
        shell.close()
    }

    private class FakePrompter(
        private val acceptHostKeys: Boolean = true,
        private val secretAnswers: ArrayDeque<String> = ArrayDeque(),
        private val challengeAnswers: List<String>? = null,
    ) : SshPrompter {
        constructor(secrets: ArrayDeque<String>) : this(acceptHostKeys = true, secretAnswers = secrets)

        val hostKeys = mutableListOf<HostKeyPrompt>()
        val secrets = mutableListOf<SecretPrompt>()
        val challenges = mutableListOf<AuthChallenge>()

        override suspend fun confirmHostKey(prompt: HostKeyPrompt) = acceptHostKeys.also { hostKeys += prompt }
        override suspend fun askSecret(prompt: SecretPrompt) = secretAnswers.removeFirstOrNull().also { secrets += prompt }
        override suspend fun answerChallenge(challenge: AuthChallenge) = challengeAnswers.also { challenges += challenge }
    }

    private class FakeConnections : SshConnectionDatasource {
        private val byId = mutableMapOf<Uuid, SshConnection>()
        fun add(connection: SshConnection) = byId.put(connection.id, connection)
        override fun index(): Flow<List<SshConnection>> = flowOf(byId.values.toList())
        override suspend fun get(id: Uuid) = byId[id]
        override suspend fun upsert(connection: SshConnection) { byId[connection.id] = connection }
        override suspend fun delete(id: Uuid) { byId.remove(id) }
    }

    private class FakeKeys : SshKeyDatasource by mockk() {
        private val secrets = mutableMapOf<Uuid, SshKeySecret>()

        fun add(secret: SshKeySecret): Uuid = Uuid.random().also { secrets[it] = secret }

        override suspend fun get(id: Uuid) =
            if (id in secrets) SshKeyInfo(id, "test key", Algorithm.Ed25519, SshKeyType.DEVICE) else null

        override suspend fun getSecret(id: Uuid) = secrets[id]
    }

    /** Just enough of the app's key service: report whether [key] decrypts with [passphrase]. */
    private object ParsingKeyService : SshKeyService by mockk() {
        override fun validate(key: String, passphrase: String?): SshKeyValidationResult = try {
            SecurityUtils.loadKeyPairIdentities(
                null,
                null,
                key.byteInputStream(),
                passphrase?.let(FilePasswordProvider::of) ?: FilePasswordProvider.EMPTY,
            ).first()
            SshKeyValidationResult.Success(isEncrypted = passphrase != null, algorithm = Algorithm.Ed25519)
        } catch (_: Exception) {
            SshKeyValidationResult.InvalidPassphrase
        }
    }

    /** Asks for a one-time code; only "123456" passes. */
    private object OtpAuthenticator : KeyboardInteractiveAuthenticator {
        override fun generateChallenge(session: ServerSession, username: String, lang: String?, subMethods: String?) =
            InteractiveChallenge().apply {
                interactionName = "OTP"
                addPrompt("Verification code: ", true)
            }

        override fun authenticate(session: ServerSession, username: String, responses: List<String>) =
            username == "otp-user" && responses == listOf("123456")
    }

    /** Echoes every byte back, which is all a terminal round-trip test needs. */
    private class EchoShell : Command {
        private lateinit var input: InputStream
        private lateinit var output: OutputStream
        private var exit: ExitCallback? = null

        override fun setInputStream(`in`: InputStream) { input = `in` }
        override fun setOutputStream(out: OutputStream) { output = out }
        override fun setErrorStream(err: OutputStream) = Unit
        override fun setExitCallback(callback: ExitCallback) { exit = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            thread(isDaemon = true) {
                runCatching {
                    val buffer = ByteArray(1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        output.flush()
                    }
                }
                exit?.onExit(0)
            }
        }

        override fun destroy(channel: ChannelSession) = Unit
    }
}

private fun KeyPair.encrypted(passphrase: String): String = write(
    OpenSSHKeyEncryptionContext().apply {
        password = passphrase
        cipherName = "AES"
        cipherType = "256"
    },
)

private fun KeyPair.unencrypted(): String = write(options = null)

private fun KeyPair.write(options: OpenSSHKeyEncryptionContext?): String {
    val out = ByteArrayOutputStream()
    OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(this, "test", options, out)
    return out.toString(Charsets.UTF_8)
}

private fun findExecutable(name: String): Path? =
    (System.getenv("PATH").orEmpty().split(File.pathSeparator) + listOf("/usr/bin", "/opt/homebrew/bin", "/usr/local/bin"))
        .map { Path.of(it, name) }
        .firstOrNull { Files.isExecutable(it) }
