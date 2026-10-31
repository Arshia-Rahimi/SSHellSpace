package com.sshellspace.feature.terminal.data.ssh

import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import java.net.SocketAddress
import java.nio.file.Path
import java.security.PublicKey
import org.apache.sshd.client.config.hosts.KnownHostEntry
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.util.net.SshdSocketAddress

/**
 * Checks host keys against an OpenSSH known_hosts file and asks the user about unknown or changed keys.
 * Accepted keys are written back to the file, like OpenSSH does.
 */
internal class PromptingKnownHostsVerifier(
    private val context: ConnectionContext,
    file: Path,
) : KnownHostsServerKeyVerifier(UnknownKeyPrompt(context), file) {

    override fun acceptModifiedServerKey(
        clientSession: ClientSession,
        remoteAddress: SocketAddress,
        entry: KnownHostEntry,
        expected: PublicKey,
        actual: PublicKey,
    ): Boolean = context.confirm(actual, expected)

    override fun handleRevokedKey(clientSession: ClientSession, remoteAddress: SocketAddress, serverKey: PublicKey) {
        context.failure = "The host key for ${context.connection.host} is marked as revoked in known_hosts."
        super.handleRevokedKey(clientSession, remoteAddress, serverKey)
    }

    /** Matches and records entries under the saved host name, not the local tunnel address of a jump. */
    override fun resolveHostNetworkIdentities(
        clientSession: ClientSession,
        remoteAddress: SocketAddress,
    ): Collection<SshdSocketAddress> = listOf(context.target)

    private class UnknownKeyPrompt(private val context: ConnectionContext) : ServerKeyVerifier {
        override fun verifyServerKey(session: ClientSession, remoteAddress: SocketAddress, key: PublicKey) =
            context.confirm(key, expected = null)
    }
}

private fun ConnectionContext.confirm(key: PublicKey, expected: PublicKey?): Boolean {
    val prompt = HostKeyPrompt(
        host = connection.host,
        port = connection.port,
        keyType = KeyUtils.getKeyType(key),
        fingerprint = KeyUtils.getFingerPrint(key),
        expectedFingerprint = expected?.let(KeyUtils::getFingerPrint),
    )
    return ask { confirmHostKey(prompt) }.also { accepted -> if (!accepted) cancel() }
}
