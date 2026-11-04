package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.hyperlinks.HyperlinkInfo
import ai.rever.bossterm.compose.hyperlinks.HyperlinkType
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BossTermLinksTest {

    private val opened = mutableListOf<URI>()

    private fun click(url: String, patternId: String = "builtin:http") = handleTerminalLink(
        HyperlinkInfo(
            url = url,
            type = HyperlinkType.entries.first(),
            patternId = patternId,
            matchedText = url,
            isFile = false,
            isFolder = false,
            scheme = null,
            isBuiltin = true,
        ),
        browse = { opened += it },
    )

    @Test
    fun `http links open in the browser`() {
        assertTrue(click("https://example.com/a?b=c"))

        assertEquals(listOf(URI("https://example.com/a?b=c")), opened)
    }

    @Test
    fun `only http and https count as browser links`() {
        assertNotNull(browserUri("http://example.com"))
        assertNull(browserUri("file:///Applications/Calculator.app"))
        assertNull(browserUri("ssh://example.com"))
        assertNull(browserUri("smb://example.com/share"))
        assertNull(browserUri("/Applications/Calculator.app"))
        assertNull(browserUri("http:///no-host"))
        assertNull(browserUri("https://example.com/a b"))
    }

    @Test
    fun `an ampersand reaches the browser as a URL, never a shell`() {
        click("https://x.example/?a&calc.exe")

        assertEquals("https://x.example/?a&calc.exe", opened.single().toString())
    }

    @Test
    fun `other links printed in the terminal do nothing`() {
        assertTrue(click("file:///Applications/Calculator.app", patternId = "builtin:file"))
        assertTrue(click("/etc/passwd", patternId = "builtin:path"))

        assertEquals(emptyList(), opened)
    }

    @Test
    fun `a local command asking to open a file still works`() {
        assertFalse(click("/Users/me/report.pdf", patternId = "osc:open-target"))
    }
}
