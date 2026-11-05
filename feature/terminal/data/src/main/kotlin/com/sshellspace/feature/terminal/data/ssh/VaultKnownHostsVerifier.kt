package com.sshellspace.feature.terminal.data.ssh

import com.sshellspace.core.data.KnownHostDatasource
import com.sshellspace.core.data.parseVaultKnownHostLine
import java.net.SocketAddress
import java.nio.file.Path
import java.security.PublicKey
import kotlinx.coroutines.runBlocking
import org.apache.sshd.client.config.hosts.KnownHostEntry
import org.apache.sshd.client.session.ClientSession

/**
 * Checks host keys against the vault's own list instead of a file. Only where entries are kept differs: matching,
 * revoking and asking about unknown or changed keys are all the file verifier's, so the two can't drift apart.
 *
 * MINA's callbacks are blocking, so this reads and writes the vault on its thread, as [ConnectionContext.ask] does.
 */
internal class VaultKnownHostsVerifier(
    private val context: ConnectionContext,
    private val knownHosts: KnownHostDatasource,
) : PromptingKnownHostsVerifier(context, UNUSED_FILE) {

    override fun verifyServerKey(clientSession: ClientSession, remoteAddress: SocketAddress, serverKey: PublicKey): Boolean {
        val entries = try {
            loadEntries(clientSession)
        } catch (_: IllegalStateException) {
            // Treating this as an empty list would ask to trust a key the vault may well hold a different one for.
            context.failure = "The vault locked while connecting."
            return false
        }
        setLoadedHostsEntries(entries)
        return acceptKnownHostEntries(clientSession, remoteAddress, serverKey, entries)
    }

    private fun loadEntries(session: ClientSession): List<HostEntryPair> =
        runBlocking { knownHosts.lines() }.mapNotNull { line ->
            parseVaultKnownHostLine(line) ?: return@mapNotNull null
            val entry = KnownHostEntry.parseKnownHostEntry(line)
            HostEntryPair(entry, resolveHostKey(session, entry, fallbackPublicKeyEntryResolver))
        }

    override fun updateKnownHostsFile(
        clientSession: ClientSession,
        remoteAddress: SocketAddress,
        serverKey: PublicKey,
        file: Path,
        knownHosts: Collection<HostEntryPair>,
    ): KnownHostEntry? {
        val entry = prepareKnownHostEntry(clientSession, remoteAddress, serverKey) ?: return null
        runBlocking { this@VaultKnownHostsVerifier.knownHosts.add(listOf(entry.configLine)) }
        return entry
    }

    override fun updateModifiedServerKey(
        clientSession: ClientSession,
        remoteAddress: SocketAddress,
        match: HostEntryPair,
        actual: PublicKey,
        file: Path,
        knownHosts: Collection<HostEntryPair>,
    ) {
        val old = match.hostEntry.configLine
        val new = prepareModifiedServerKeyLine(clientSession, remoteAddress, match.hostEntry, old, match.serverKey, actual)
        runBlocking { this@VaultKnownHostsVerifier.knownHosts.replace(old, new) }
    }

    private companion object {
        /** The file verifier needs a path, but everything that would use it is overridden here. */
        val UNUSED_FILE: Path = Path.of("vault-known-hosts")
    }
}
