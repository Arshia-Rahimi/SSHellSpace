package com.sshellspace.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry

class KnownHostLinesTest {
    private val publicKey = KeyUtils.generateKeyPair("ssh-ed25519", 256).public
    private val key = PublicKeyEntry.toString(publicKey)
    private val hashed = "|1|F1E1KeoE/eEWhi10WpGv4OdiO6Y=|3988QV0VE8wmZL7suNrYQLITLCg="

    @Test
    fun `reads a plain line`() {
        val host = assertNotNull(parseKnownHostLine("example.com $key"))

        assertEquals(listOf("example.com"), host.hosts)
        assertEquals("ssh-ed25519", host.keyType)
        assertEquals(false, host.isRevoked)
    }

    @Test
    fun `shows the fingerprint the way a host key prompt does`() {
        assertEquals(KeyUtils.getFingerPrint(publicKey), parseKnownHostLine("example.com $key")?.fingerprint)
    }

    @Test
    fun `shows ports and several names plainly`() {
        assertEquals(
            listOf("example.com:2222", "10.0.0.1"),
            parseKnownHostLine("[example.com]:2222,10.0.0.1 $key")?.hosts,
        )
    }

    @Test
    fun `a hashed line has no names to show`() {
        assertEquals(emptyList(), parseKnownHostLine("$hashed $key")?.hosts)
    }

    @Test
    fun `reads markers`() {
        assertEquals(true, parseKnownHostLine("@revoked example.com $key")?.isRevoked)
        assertEquals(listOf("example.com"), parseKnownHostLine("@revoked example.com $key")?.hosts)
        assertEquals(true, parseKnownHostLine("@cert-authority *.example.com $key")?.isCertificateAuthority)
    }

    @Test
    fun `a line that isn't an entry is null rather than a crash`() {
        assertNull(parseKnownHostLine("not a known hosts line"))
        assertNull(parseKnownHostLine("example.com ssh-ed25519 !!!notbase64!!!"))
        assertNull(parseKnownHostLine("example.com"))
        assertNull(parseKnownHostLine("exa\u0007mple.com $key"))
    }

    @Test
    fun `a file's comments and blanks are skipped, repeats collapse, and bad lines are counted`() {
        val parsed = parseKnownHostsText(
            """
            # a comment

            example.com $key
            example.com $key # trailing comment
            garbage
            other.example.com $key
            """.trimIndent(),
        )

        assertEquals(listOf("example.com", "other.example.com"), parsed.hosts.flatMap { it.hosts })
        assertEquals(1, parsed.unreadable)
    }

    @Test
    fun `the vault's list takes lines that name their hosts`() {
        assertNotNull(parseVaultKnownHostLine("example.com $key"))
        assertNotNull(parseVaultKnownHostLine("[example.com]:2222,10.0.0.1 $key"))
        assertNotNull(parseVaultKnownHostLine("$hashed $key"))
        assertNotNull(parseVaultKnownHostLine("@revoked example.com $key"))
    }

    @Test
    fun `the vault's list refuses what could trust a key for many hosts`() {
        assertNull(parseVaultKnownHostLine("* $key"))
        assertNull(parseVaultKnownHostLine("*.example.com $key"))
        assertNull(parseVaultKnownHostLine("exampl?.com $key"))
        assertNull(parseVaultKnownHostLine("example.com,!other.com $key"))
        assertNull(parseVaultKnownHostLine("example.com,* $key"))
        assertNull(parseVaultKnownHostLine("@cert-authority example.com $key"))
    }

    @Test
    fun `extra space after a marker doesn't hide a wildcard from the vault's list`() {
        assertNull(parseVaultKnownHostLine("@revoked  * $key"))
        assertNull(parseVaultKnownHostLine("@revoked\t\t* $key"))
        assertEquals(listOf("example.com"), parseKnownHostLine("@revoked  example.com $key")?.hosts)
    }

    @Test
    fun `an unknown marker isn't an entry, since MINA would trust it like an unmarked line`() {
        assertNull(parseKnownHostLine("@x example.com $key"))
        assertNull(parseKnownHostLine("@Revoked example.com $key"))
        assertNull(parseVaultKnownHostLine("@x  * $key"))
        assertNull(parseVaultKnownHostLine("@x\t\t* $key"))
    }

    @Test
    fun `a plain host is one name or address`() {
        listOf("example.com", "web-1.internal", "_dmarc.example.com", "localhost", "10.0.0.1", "::1", "2001:db8::1", "fe80::1%en0")
            .forEach { assertTrue(isPlainHost(it), it) }
    }

    @Test
    fun `a pattern, a marker or a second word isn't a plain host`() {
        listOf(
            "",
            "*",
            "prod.internal,*",
            "a,b",
            "exampl?.com",
            "!example.com",
            "@cert-authority *",
            "* ssh-ed25519 AAAA",
            "|1|salt|hash",
            "[example.com]:2222",
            "[::1]",
            "fe80::1%a,*",
            "not:an:address",
            "-oProxyCommand=x",
            "example.com\nevil.com",
            "exämple.com",
        ).forEach { assertFalse(isPlainHost(it), it) }
    }

    @Test
    fun `the vault's list refuses a key that can't be used`() {
        assertTrue(parseKnownHostLine("example.com ssh-ed25519 QUJDRA==") != null)
        assertNull(parseVaultKnownHostLine("example.com ssh-ed25519 QUJDRA=="))
        // Three words can pass for a host, a key type and a key.
        assertTrue(parseKnownHostLine("just some word") != null)
        assertNull(parseVaultKnownHostLine("just some word"))
    }
}
