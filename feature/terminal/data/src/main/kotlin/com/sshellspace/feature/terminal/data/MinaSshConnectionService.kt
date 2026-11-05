package com.sshellspace.feature.terminal.data

import com.sshellspace.core.data.KnownHostDatasource
import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.data.isPlainHost
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshKeyType
import com.sshellspace.feature.terminal.data.ssh.ConnectionContext
import com.sshellspace.feature.terminal.data.sftp.MinaSftpSession
import com.sshellspace.feature.terminal.data.ssh.agent.SshAgentLocator
import com.sshellspace.feature.terminal.domain.SESSION_LOST_EXIT_CODE
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.SftpSession
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshForwardHandle
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.SshShellHandle
import java.io.File
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.security.KeyPair
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.NamedResource
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.session.SessionHeartbeatController
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.sftp.client.SftpClientFactory

class MinaSshConnectionService(
    private val sshClient: SshClient,
    private val connectionDatasource: SshConnectionDatasource,
    private val keyDatasource: SshKeyDatasource,
    private val sshKeyService: SshKeyService,
    private val agentLocator: SshAgentLocator,
    private val vaultKnownHosts: KnownHostDatasource,
) : SshConnectionService {

    override suspend fun connect(connectionId: Uuid, prompter: SshPrompter): SshConnectResult =
        withContext(Dispatchers.IO) {
            val chain = try {
                resolveChain(connectionId)
            } catch (e: ConnectFailure) {
                return@withContext SshConnectResult.Failed(e.message)
            }

            val sessions = mutableListOf<ClientSession>()
            var hop = chain.first()
            try {
                for (next in chain) {
                    hop = next
                    val tunnel = sessions.lastOrNull()?.let { tunnel(it, hop) }
                    val address = tunnel?.boundAddress ?: SshdSocketAddress(hop.host, hop.port)
                    sessions += openSession(hop, address, prompter)
                    // Connected, so stop listening: otherwise any local program could reach this hop through the port.
                    tunnel?.close()
                }
                SshConnectResult.Success(MinaSshSession(sessions.toList()), chain.last())
            } catch (e: CancellationException) {
                sessions.closeAll()
                throw e
            } catch (_: UserCancelled) {
                sessions.closeAll()
                SshConnectResult.Cancelled
            } catch (e: ConnectFailure) {
                sessions.closeAll()
                SshConnectResult.Failed(e.message)
            } catch (e: Exception) {
                sessions.closeAll()
                SshConnectResult.Failed(describe(e, hop))
            }
        }

    /** The target and its jump hosts, outermost jump first. */
    private suspend fun resolveChain(connectionId: Uuid): List<SshConnection> {
        val chain = ArrayDeque<SshConnection>()
        var nextId: Uuid? = connectionId
        while (nextId != null) {
            if (chain.any { it.id == nextId }) throw ConnectFailure("The jump hosts of ${chain.last().name} form a loop.")
            if (chain.size > MAX_JUMPS) throw ConnectFailure("Too many jump hosts.")
            val connection = connectionDatasource.get(nextId) ?: throw ConnectFailure(
                if (chain.isEmpty()) "This connection no longer exists." else "The jump host of ${chain.first().name} was deleted.",
            )
            // Saving checks this too, but an imported vault's connections were never saved here.
            if (!isPlainHost(connection.host)) {
                throw ConnectFailure("The host of ${connection.name} isn't a host name or IP address.")
            }
            chain.addFirst(connection)
            nextId = connection.options.jumpHostId
        }
        return chain
    }

    /** Forwards a local port through [jump] to [hop], like OpenSSH's ProxyJump. */
    private fun tunnel(jump: ClientSession, hop: SshConnection) =
        jump.createLocalPortForwardingTracker(SshdSocketAddress.LOCALHOST_ADDRESS, SshdSocketAddress(hop.host, hop.port))

    private suspend fun openSession(hop: SshConnection, address: SshdSocketAddress, prompter: SshPrompter): ClientSession {
        val context = ConnectionContext(
            connection = hop,
            prompter = prompter,
            keyPairs = loadKeys(hop, prompter),
            vaultKnownHosts = vaultKnownHosts.takeIf { loginMovesWithVault(hop) },
        )
        if (context.useAgent) checkAgent()

        val future = sshClient.connect(
            hop.username,
            address.hostName,
            address.port,
            AttributeRepository.ofKeyValuePair(ConnectionContext.KEY, context),
            null,
        )
        val session = runInterruptible {
            future.verify(Duration.ofSeconds(hop.options.connectTimeoutSeconds.toLong())).clientSession
        }
        try {
            // No tight timeout: auth can wait on the user (host key, password, 2FA code).
            runInterruptible { session.auth().verify(AUTH_TIMEOUT) }
        } catch (e: Exception) {
            session.close(true)
            throw when {
                context.isCancelled -> UserCancelled()
                context.failure != null -> ConnectFailure(context.failure!!)
                e is CancellationException -> e
                session.isAuthenticated -> e
                else -> ConnectFailure(authFailure(hop, e))
            }
        }

        val keepAlive = hop.options.keepAliveIntervalSeconds
        if (keepAlive > 0) {
            session.setSessionHeartbeat(
                SessionHeartbeatController.HeartbeatType.IGNORE,
                Duration.ofSeconds(keepAlive.toLong()),
            )
        }
        return session
    }

    /**
     * Whether this hop could still log in with the vault on another device, in which case its trusted host keys need to
     * go along. Anything that relies on this computer's own files or agent stays with this computer's known_hosts.
     * Decided per hop, since a jump host and its target can each log in differently.
     */
    private suspend fun loginMovesWithVault(hop: SshConnection): Boolean = when (val auth = hop.auth) {
        is SshConnectionAuth.Password, is SshConnectionAuth.KeyboardInteractive -> true
        is SshConnectionAuth.StoredKey -> keyDatasource.get(auth.keyId)?.type == SshKeyType.VAULT
        is SshConnectionAuth.KeyFile, SshConnectionAuth.Agent -> false
    }

    private suspend fun loadKeys(hop: SshConnection, prompter: SshPrompter): List<KeyPair> =
        when (val auth = hop.auth) {
            is SshConnectionAuth.StoredKey -> {
                val info = keyDatasource.get(auth.keyId)
                val secret = keyDatasource.getSecret(auth.keyId)
                    ?: throw ConnectFailure("The SSH key for ${hop.name} was deleted.")
                val label = info?.name ?: "the SSH key"
                val privateKey = try {
                    secret.privateKey
                } catch (e: java.io.IOException) {
                    throw ConnectFailure("Couldn't read the key file for $label: ${e.message}")
                }
                val passphrase = (secret as? com.sshellspace.core.domain.models.SshKeySecret.Device)?.passphrase
                decryptKeys(privateKey, passphrase, label, prompter)
            }

            is SshConnectionAuth.KeyFile -> {
                val file = File(expandHome(auth.path))
                if (!file.isFile) throw ConnectFailure("Key file not found: ${file.path}")
                decryptKeys(file.readText(), auth.passphrase, file.name, prompter)
            }

            else -> emptyList()
        }

    /** Parses [privateKey], asking for the passphrase until it is right, the user cancels, or attempts run out. */
    private suspend fun decryptKeys(
        privateKey: String,
        savedPassphrase: String?,
        label: String,
        prompter: SshPrompter,
    ): List<KeyPair> {
        var passphrase = savedPassphrase
        repeat(MAX_PASSPHRASE_ATTEMPTS + 1) { attempt ->
            when (val result = sshKeyService.validate(privateKey, passphrase)) {
                is SshKeyValidationResult.Success -> {
                    val provider = passphrase?.let(FilePasswordProvider::of) ?: FilePasswordProvider.EMPTY
                    return SecurityUtils.loadKeyPairIdentities(
                        null,
                        NamedResource.ofName(label),
                        privateKey.byteInputStream(),
                        provider,
                    ).toList()
                }

                is SshKeyValidationResult.InvalidSshKeyFormat ->
                    throw ConnectFailure("$label isn't a valid private key: ${result.errorMessage}")

                SshKeyValidationResult.InvalidPassphrase -> {
                    if (attempt == MAX_PASSPHRASE_ATTEMPTS) return@repeat
                    passphrase = prompter.askSecret(
                        SecretPrompt(
                            title = "Key passphrase",
                            message = "$label is encrypted. Enter its passphrase.",
                            label = "Passphrase",
                            error = "Wrong passphrase, try again.".takeIf { passphrase != null },
                        ),
                    ) ?: throw UserCancelled()
                }
            }
        }
        throw ConnectFailure("Wrong passphrase for $label.")
    }

    /** Fails early with a clear message instead of letting `publickey` auth fail without explanation. */
    private fun checkAgent() {
        val path = agentLocator.socketPath
            ?: throw ConnectFailure("No SSH agent found: SSH_AUTH_SOCK isn't set. Start ssh-agent and add keys with ssh-add.")
        val identities = try {
            agentLocator.open().use { agent -> agent.identities.count() }
        } catch (e: Exception) {
            throw ConnectFailure("Couldn't reach the SSH agent at $path: ${e.message}")
        }
        if (identities == 0) throw ConnectFailure("The SSH agent has no keys. Add one with ssh-add.")
    }

    private fun authFailure(hop: SshConnection, e: Exception): String {
        val reason = e.rootMessage()
        return if (reason.contains("authentication", ignoreCase = true) || reason.contains("No more", ignoreCase = true)) {
            "Authentication failed for ${hop.username}@${hop.host}. Check the username and credentials."
        } else {
            describe(e, hop)
        }
    }

    private fun describe(e: Throwable, hop: SshConnection): String {
        val endpoint = "${hop.host}:${hop.port}"
        return when (generateSequence(e) { it.cause }.firstOrNull { it.isNetworkError() }) {
            is UnknownHostException, is UnresolvedAddressException -> "Couldn't find the host ${hop.host}."
            is ConnectException -> "$endpoint refused the connection."
            is NoRouteToHostException -> "No route to $endpoint."
            is SocketTimeoutException -> "Timed out connecting to $endpoint."
            else -> if (e.rootMessage().contains("timeout", ignoreCase = true)) {
                "Timed out connecting to $endpoint."
            } else {
                "Couldn't connect to $endpoint: ${e.rootMessage()}"
            }
        }
    }

    private fun Throwable.isNetworkError() = this is UnknownHostException || this is UnresolvedAddressException ||
        this is ConnectException || this is NoRouteToHostException || this is SocketTimeoutException

    private fun Throwable.rootMessage(): String =
        generateSequence(this) { it.cause }.mapNotNull { it.message }.lastOrNull() ?: javaClass.simpleName

    private fun List<ClientSession>.closeAll() = asReversed().forEach { runCatching { it.close(true) } }

    private fun expandHome(path: String): String =
        if (path == "~" || path.startsWith("~/")) System.getProperty("user.home") + path.removePrefix("~") else path

    private class UserCancelled : Exception()
    private class ConnectFailure(override val message: String) : Exception(message)

    private companion object {
        const val MAX_JUMPS = 8
        const val MAX_PASSPHRASE_ATTEMPTS = 3
        val AUTH_TIMEOUT: Duration = Duration.ofMinutes(10)
    }
}

/** The target session plus the jump host sessions it tunnels through, outermost first. */
private class MinaSshSession(private val sessions: List<ClientSession>) : ShellSession.Ssh {
    private val target = sessions.last()

    override val isAuthenticated: Boolean
        get() = target.isAuthenticated

    override fun openShell(columns: Int, rows: Int): SshShellHandle {
        val channel = target.createShellChannel()
        channel.ptyType = "xterm-256color"
        channel.ptyColumns = columns
        channel.ptyLines = rows
        channel.open().verify(10L, TimeUnit.SECONDS)
        return MinaSshShellHandle(this, channel)
    }

    override fun startForward(forward: PortForward): SshForwardHandle {
        val bind = SshdSocketAddress(forward.bindHost, forward.bindPort)
        val destination = SshdSocketAddress(forward.destinationHost, forward.destinationPort)
        val tracker = when (forward.type) {
            PortForwardType.Local -> target.createLocalPortForwardingTracker(bind, destination)
            PortForwardType.Remote -> target.createRemotePortForwardingTracker(bind, destination)
            PortForwardType.Dynamic -> target.createDynamicPortForwardingTracker(bind)
        }
        return object : SshForwardHandle {
            override val boundAddress: String = tracker.boundAddress.let { "${it.hostName}:${it.port}" }
            override fun close() = tracker.close()
        }
    }

    override fun openSftp(): SftpSession = try {
        MinaSftpSession(SftpClientFactory.instance().createSftpClient(target))
    } catch (e: java.io.IOException) {
        throw SftpFailure(
            "Connected, but the server wouldn't start SFTP. Check for a Subsystem sftp line in its sshd_config, " +
                "and that the login doesn't print text from its shell profile. (${e.message})",
        )
    }

    override fun onClose(listener: () -> Unit) {
        target.addCloseFutureListener { listener() }
    }

    override fun close() {
        sessions.asReversed().forEach { runCatching { it.close(false) } }
    }
}

private class MinaSshShellHandle(
    private val session: MinaSshSession,
    private val channel: ChannelShell,
) : SshShellHandle {

    private val stdin = channel.invertedIn
    // A reader keeps multi-byte UTF-8 characters intact across read boundaries.
    private val stdout = InputStreamReader(channel.invertedOut, Charsets.UTF_8)
    private val exitCodeRef = AtomicReference<Int?>(null)

    override suspend fun write(data: String) = writeBytes(data.toByteArray(Charsets.UTF_8))

    override suspend fun writeBytes(data: ByteArray) = withContext(Dispatchers.IO) {
        stdin.write(data)
        stdin.flush()
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val buf = CharArray(8192)
        val n = stdout.read(buf)
        if (n < 0) null else String(buf, 0, n)
    }

    override fun isAlive(): Boolean = !channel.isClosed && !channel.isClosing

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            channel.close(false)
            session.close()
        }
    }

    override suspend fun waitForExit(): Int = withContext(Dispatchers.IO) {
        channel.waitFor(setOf(ClientChannelEvent.CLOSED), 0L)
        (channel.exitStatus ?: SESSION_LOST_EXIT_CODE).also {
            exitCodeRef.set(it)
            session.close()
        }
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        channel.sendWindowChange(columns, rows)
    }

    override fun exitCode(): Int? = exitCodeRef.get() ?: channel.exitStatus
}
