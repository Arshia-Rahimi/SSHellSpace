package com.sshellspace.feature.terminal.data.ssh

import java.nio.file.Path
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.compression.BuiltinCompressions
import org.apache.sshd.common.compression.Compression
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener

/**
 * Applies a [ConnectionContext] to each new client session. MINA calls [sessionCreated] before it sends its
 * key exchange init, so host key checking, compression and auth all see the per-connection settings.
 */
internal class SessionConfigurer(
    private val knownHosts: () -> Path,
) : SessionListener {

    override fun sessionCreated(session: Session) {
        val clientSession = session as? ClientSession ?: return
        val context = clientSession.connectionContext?.getAttribute(ConnectionContext.KEY) ?: return

        clientSession.serverKeyVerifier = context.vaultKnownHosts?.let { VaultKnownHostsVerifier(context, it) }
            ?: PromptingKnownHostsVerifier(context, knownHosts())
        clientSession.userInteraction = SessionUserInteraction(context)
        clientSession.compressionFactories = if (context.connection.options.compression) COMPRESSED else UNCOMPRESSED
        context.savedPassword?.let(clientSession::addPasswordIdentity)
        context.keyPairs.forEach(clientSession::addPublicKeyIdentity)
    }

    private companion object {
        // Order is preference: the first entry the server also supports wins.
        val COMPRESSED: List<NamedFactory<Compression>> =
            listOf(BuiltinCompressions.delayedZlib, BuiltinCompressions.zlib, BuiltinCompressions.none)
        val UNCOMPRESSED: List<NamedFactory<Compression>> = listOf(BuiltinCompressions.none)
    }
}
