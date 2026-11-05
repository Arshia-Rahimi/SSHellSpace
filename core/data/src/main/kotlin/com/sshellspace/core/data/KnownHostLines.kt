package com.sshellspace.core.data

import com.sshellspace.core.domain.models.KnownHost
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import org.apache.sshd.client.config.hosts.KnownHostEntry
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver

/** The OpenSSH known_hosts file, shared with the `ssh` command line tool. */
val DefaultKnownHostsPath: Path = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts")

private const val MARKER_REVOKED = "revoked"
private const val MARKER_CERTIFICATE_AUTHORITY = "cert-authority"
private const val COMMENT = '#'
private const val MARKER_PREFIX = '@'
private const val WILDCARDS = "*?!"
private val BRACKETED_HOST = Regex("""^\[(.+)]:(\d+)$""")

/** What the text of a known_hosts file holds. */
data class ParsedKnownHosts(
    val hosts: List<KnownHost>,
    /** Lines that had something on them, but nothing that could be read as an entry. */
    val unreadable: Int,
)

fun parseKnownHostsText(text: String): ParsedKnownHosts {
    val lines = text.lineSequence().map { it.substringBefore(COMMENT).trim() }.filter { it.isNotEmpty() }.toList()
    // A file can repeat a line, and these go on to be keys in a list that can't show one twice.
    val hosts = lines.mapNotNull(::parseKnownHostLine).distinctBy { it.line }
    return ParsedKnownHosts(hosts, unreadable = lines.count { parseKnownHostLine(it) == null })
}

/** Null when [line] isn't an entry. Doesn't check that its key is one this app could use. */
fun parseKnownHostLine(line: String): KnownHost? {
    if (line.any { it.isISOControl() && it != '\t' }) return null
    val entry = try {
        KnownHostEntry.parseKnownHostEntry(line)
    } catch (_: RuntimeException) {
        null
    }
    val key = entry?.keyEntry ?: return null
    return KnownHost(
        line = entry.configLine,
        hosts = if (entry.hashedEntry != null) emptyList() else hostField(entry.configLine).split(',').map(::displayHost),
        keyType = key.keyType,
        fingerprint = fingerprint(key.keyData),
        isRevoked = entry.marker == MARKER_REVOKED,
        isCertificateAuthority = entry.marker == MARKER_CERTIFICATE_AUTHORITY,
    )
}

/**
 * Null when a vault's list must not hold [line]. `* ssh-ed25519 ...` and `@cert-authority *` would trust one key for
 * every host, so anyone who got such a line into an imported file or a crafted vault could impersonate any server
 * without a prompt. Each line has to name its hosts, which a hashed one does. Revoking is fine: it only refuses.
 */
fun parseVaultKnownHostLine(line: String): KnownHost? =
    parseKnownHostLine(line)?.takeIf { !it.trustsManyHosts && it.hasUsableKey }

/** It's a wildcard or a certificate authority, rather than naming the hosts it applies to. */
val KnownHost.trustsManyHosts: Boolean get() = isCertificateAuthority || hostField(line).any { it in WILDCARDS }

/** Its key decodes, and is of a type this app could check a server against. A line can parse without that being so. */
val KnownHost.hasUsableKey: Boolean
    get() = runCatching {
        KnownHostEntry.parseKnownHostEntry(line).keyEntry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING)
    }.getOrNull() != null

/** The host names of a line, which come after its marker if it has one. */
private fun hostField(line: String): String {
    val fields = line.split(' ')
    return if (fields.first().startsWith(MARKER_PREFIX)) fields[1] else fields[0]
}

private fun displayHost(pattern: String): String =
    BRACKETED_HOST.matchEntire(pattern)?.let { "${it.groupValues[1]}:${it.groupValues[2]}" } ?: pattern

/** Worked out from the key's bytes, so it can show keys of a type this app can't otherwise use. */
private fun fingerprint(keyData: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(keyData)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}
