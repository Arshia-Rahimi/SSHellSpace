package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshAuthMethod
import com.sshellspace.feature.terminal.domain.SshAuthenticator
import com.sshellspace.feature.terminal.domain.SshConnectionConfig
import com.sshellspace.feature.terminal.domain.SshConnectionResult
import com.sshellspace.feature.terminal.domain.SshShellHandle
import java.net.SocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.keyprovider.FileKeyPairProvider

private const val VERIFICATION_TIMEOUT_SECONDS = 10L
private const val SSH_DEFAULT_PORT = 22

class MinaSshAuthenticator : SshAuthenticator {
    private val sshClient: SshClient by lazy {
        SshClient.setUpDefaultClient().apply { start() }
    }

    override suspend fun connect(config: SshConnectionConfig): SshConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                Files.createDirectories(Paths.get(config.knownHostsPath).parent)

                var rejectedPrompt: HostKeyPrompt? = null
                sshClient.serverKeyVerifier = buildVerifier(config) { prompt ->
                    val accepted = runBlockingPrompt(config.onHostKeyPrompt, prompt)
                    if (!accepted) rejectedPrompt = prompt
                    accepted
                }

                val connectFuture = sshClient.connect(config.username, config.host, config.port)
                    .verify(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                val session = connectFuture.session

                rejectedPrompt?.let {
                    session.close(false)
                    return@withContext SshConnectionResult.HostKeyRejected(it)
                }

                applyAuth(session, config.auth)
                session.auth().verify(config.authTimeoutSeconds, TimeUnit.SECONDS)

                if (!session.isAuthenticated) {
                    session.close(false)
                    return@withContext SshConnectionResult.AuthFailed(
                        "Authentication failed for ${config.username}@${config.host}"
                    )
                }

                SshConnectionResult.Success(MinaSshSession(session))
            } catch (t: Throwable) {
                SshConnectionResult.ConnectFailed(t)
            }
        }

    private fun applyAuth(session: ClientSession, auth: SshAuthMethod) {
        when (auth) {
            is SshAuthMethod.Agent -> {
                // Requires agent support wired on SshClient (see agentFactory setup);
                // no extra per-session code needed once that's configured.
            }

            is SshAuthMethod.PrivateKey -> {
                val provider = FileKeyPairProvider(Paths.get(auth.path))
                auth.passphrase?.let {
                    provider.passwordFinder = FilePasswordProvider.of(String(it))
                }
                provider.loadKeys(session).forEach { session.addPublicKeyIdentity(it) }
            }

            is SshAuthMethod.Password -> {
                session.addPasswordIdentity(String(auth.password))
            }
        }
    }

    private fun buildVerifier(
        config: SshConnectionConfig,
        onPrompt: (HostKeyPrompt) -> Boolean,
    ): ServerKeyVerifier {
        val fallback = ServerKeyVerifier { _, remoteAddress, serverKey ->
            onPrompt(toPrompt(remoteAddress, serverKey))
        }
        return KnownHostsServerKeyVerifier(fallback, Paths.get(config.knownHostsPath))
    }

    private fun toPrompt(address: SocketAddress, key: PublicKey): HostKeyPrompt {
        val hostPort = address.toString().trimStart('/')
        val (host, portStr) = hostPort.substringBeforeLast(':') to hostPort.substringAfterLast(':')
        return HostKeyPrompt(
            host = host,
            port = portStr.toIntOrNull() ?: SSH_DEFAULT_PORT,
            fingerprint = KeyUtils.getFingerPrint(key),
            keyType = KeyUtils.getKeyType(key) ?: "unknown",
            isChangedKey = false,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runBlockingPrompt(
        prompt: suspend (HostKeyPrompt) -> Boolean,
        request: HostKeyPrompt,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        kotlinx.coroutines.runBlocking {
            deferred.complete(prompt(request))
        }
        return deferred.getCompleted()
    }
}

private class MinaSshSession(private val session: ClientSession) : ShellSession.Ssh {

    override val isAuthenticated: Boolean
        get() = session.isAuthenticated

    override fun openShell(columns: Int, rows: Int): SshShellHandle {
        val channel = session.createShellChannel()
        channel.ptyType = "xterm-256color"
        channel.ptyColumns = columns
        channel.ptyLines = rows
        channel.open().verify(VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return MinaSshShellHandle(session, channel)
    }

    override fun close() {
        session.close(false)
    }
}

private class MinaSshShellHandle(
    private val session: ClientSession,
    private val channel: ChannelShell,
) : SshShellHandle {

    private val stdin = channel.invertedIn
    private val stdout = channel.invertedOut
    private val exitCodeRef = AtomicReference<Int?>(null)

    override suspend fun write(data: String) = writeBytes(data.toByteArray(Charsets.UTF_8))

    override suspend fun writeBytes(data: ByteArray) = withContext(Dispatchers.IO) {
        stdin.write(data); stdin.flush()
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val buf = ByteArray(8192)
        val n = stdout.read(buf)
        if (n < 0) null else String(buf, 0, n, Charsets.UTF_8)
    }

    override fun isAlive(): Boolean = !channel.isClosed && !channel.isClosing

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            channel.close(false); session.close(false)
        }
    }

    override suspend fun waitForExit(): Int = withContext(Dispatchers.IO) {
        channel.waitFor(setOf(ClientChannelEvent.CLOSED), 0L)
        (channel.exitStatus ?: -1).also { exitCodeRef.set(it) }
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        channel.sendWindowChange(columns, rows)
    }

    override fun exitCode(): Int? = exitCodeRef.get() ?: channel.exitStatus
}
