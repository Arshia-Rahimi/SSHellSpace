package com.sshellspace.feature.vault.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SshConfigParserTest {

    @Test
    fun `reads a host's settings`() {
        val host = parseSshConfig(
            """
            Host web
                HostName web.example.com
                User deploy
                Port 2222
                IdentityFile ~/.ssh/web_key
                Compression yes
                ServerAliveInterval 30
                ConnectTimeout 5
            """.trimIndent(),
        ).single()

        assertEquals("web", host.alias)
        assertEquals("web.example.com", host.hostName)
        assertEquals("deploy", host.user)
        assertEquals(2222, host.port)
        assertEquals("~/.ssh/web_key", host.identityFile)
        assertTrue(host.compression)
        assertEquals(30, host.keepAliveSeconds)
        assertEquals(5, host.connectTimeoutSeconds)
    }

    @Test
    fun `the host name defaults to the alias and the port to 22`() {
        val host = parseSshConfig("Host box").single()

        assertEquals("box", host.hostName)
        assertEquals(22, host.port)
        assertNull(host.user)
    }

    @Test
    fun `keywords ignore case and accept an equals sign, quotes and comments`() {
        val host = parseSshConfig(
            """
            # A comment
            HOST box
              hostname=box.example.com # trailing comment
              IDENTITYFILE "~/my keys/id_ed25519"
            """.trimIndent(),
        ).single()

        assertEquals("box.example.com", host.hostName)
        assertEquals("~/my keys/id_ed25519", host.identityFile)
    }

    @Test
    fun `one block can define several hosts`() {
        assertEquals(listOf("a", "b"), parseSshConfig("Host a b\n  User me").map { it.alias })
    }

    @Test
    fun `wildcard blocks aren't hosts but supply defaults`() {
        val hosts = parseSshConfig(
            """
            Host web
              User deploy
            Host *
              User fallback
              Port 2200
            """.trimIndent(),
        )

        val web = hosts.single()
        assertEquals("deploy", web.user)
        assertEquals(2200, web.port)
    }

    @Test
    fun `the first value wins, like OpenSSH`() {
        val host = parseSshConfig("Host *\n  User first\nHost box\n  User second").single()

        assertEquals("first", host.user)
    }

    @Test
    fun `patterns and negation decide which blocks apply`() {
        val hosts = parseSshConfig(
            """
            Host prod-web
            Host prod-db
            Host prod-* !prod-db
              User web
            Host prod-?b
              Port 2022
            """.trimIndent(),
        ).associateBy { it.alias }

        assertEquals("web", hosts.getValue("prod-web").user)
        assertNull(hosts.getValue("prod-db").user)
        assertEquals(2022, hosts.getValue("prod-db").port)
    }

    @Test
    fun `settings before any Host apply to every host`() {
        val host = parseSshConfig("User everyone\nHost box").single()

        assertEquals("everyone", host.user)
    }

    @Test
    fun `Match blocks are skipped`() {
        val host = parseSshConfig("Host box\nMatch host box\n  User matched\n").single()

        assertNull(host.user)
    }

    @Test
    fun `host name tokens are filled in`() {
        val host = parseSshConfig("Host box\n  HostName %h.example.com\n  IdentityFile ~/.ssh/%h").single()

        assertEquals("box.example.com", host.hostName)
        assertEquals("~/.ssh/box.example.com", host.identityFile)
    }

    @Test
    fun `IdentityFile none means no key`() {
        assertNull(parseSshConfig("Host box\n  IdentityFile none").single().identityFile)
    }

    @Test
    fun `what can't be imported is reported`() {
        val host = parseSshConfig(
            """
            Host box
              ProxyCommand nc %h %p
              LocalForward 8080 localhost:80
              IdentityFile ~/.ssh/one
              IdentityFile ~/.ssh/two
            """.trimIndent(),
        ).single()

        assertEquals(3, host.warnings.size)
        assertEquals("~/.ssh/one", host.identityFile)
    }

    @Test
    fun `includes are followed and can't recurse forever`() {
        val hosts = parseSshConfig("Include extra\nHost main") { pattern ->
            if (pattern == "extra") listOf("Host included\nInclude extra") else emptyList()
        }

        assertEquals(listOf("included", "main"), hosts.map { it.alias })
    }

    @Test
    fun `junk lines don't stop the parse`() {
        val host = parseSshConfig("Host box\nnonsense\n\n===\n  User me").single()

        assertEquals("me", host.user)
        assertFalse(host.compression)
    }
}
