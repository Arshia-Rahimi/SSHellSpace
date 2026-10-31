package com.sshellspace.feature.terminal.data.ssh

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.feature.terminal.domain.SshPrompter
import java.security.KeyPair
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.util.net.SshdSocketAddress

/**
 * Everything one hop needs while MINA sets up its session. It travels as the connection context
 * ([org.apache.sshd.client.session.ClientSession.getConnectionContext]), which MINA attaches before key exchange
 * starts, so [SessionConfigurer] can configure the session from it.
 */
internal class ConnectionContext(
    val connection: SshConnection,
    private val prompter: SshPrompter,
    /** Keys for `publickey` auth, already decrypted. */
    val keyPairs: List<KeyPair> = emptyList(),
) {
    /** The real host, even when the TCP connection goes through a jump host tunnel on localhost. */
    val target = SshdSocketAddress(connection.host, connection.port)
    val address = "${connection.username}@${connection.host}"

    val savedPassword: String? = when (val auth = connection.auth) {
        is SshConnectionAuth.Password -> auth.password
        is SshConnectionAuth.KeyboardInteractive -> auth.password
        else -> null
    }
    val useAgent = connection.auth is SshConnectionAuth.Agent

    /** Password-style prompts are only answered for the methods the user picked. */
    val allowsPasswordPrompts = connection.auth is SshConnectionAuth.Password
    val allowsChallenges = connection.auth is SshConnectionAuth.Password ||
        connection.auth is SshConnectionAuth.KeyboardInteractive

    val passwordAttempts = AtomicInteger(0)

    private val cancelled = AtomicBoolean(false)
    val isCancelled: Boolean get() = cancelled.get()

    /** Set when the connection must fail with a specific message, e.g. a revoked host key. */
    @Volatile
    var failure: String? = null

    fun cancel() = cancelled.set(true)

    /**
     * Asks the user from a MINA callback. Those callbacks are blocking, so this parks the MINA worker thread
     * until the user answers, which is how MINA expects interactive auth to work.
     */
    fun <T> ask(block: suspend SshPrompter.() -> T): T = runBlocking { prompter.block() }

    companion object {
        val KEY = AttributeRepository.AttributeKey<ConnectionContext>()
    }
}
