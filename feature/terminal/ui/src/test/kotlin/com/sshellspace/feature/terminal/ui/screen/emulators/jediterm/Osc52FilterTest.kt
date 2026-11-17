package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Osc52FilterTest {

    private val copied = mutableListOf<String>()
    private val filter = Osc52Filter { copied += it }

    private fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun `a copy is taken out of the output and handed on`() {
        val output = filter.filter("before\u001b]52;c;${encoded("copied text")}\u0007after")

        assertEquals("beforeafter", output)
        assertEquals(listOf("copied text"), copied)
    }

    @Test
    fun `the string terminator ends a copy too`() {
        val output = filter.filter("\u001b]52;c;${encoded("tmux")}\u001b\\prompt")

        assertEquals("prompt", output)
        assertEquals(listOf("tmux"), copied)
    }

    @Test
    fun `a copy split across reads is put back together`() {
        val sequence = "\u001b]52;c;${encoded("split across reads")}\u001b\\"
        val output = sequence.chunked(3).joinToString("") { filter.filter(it) }

        assertEquals("", output)
        assertEquals(listOf("split across reads"), copied)
    }

    @Test
    fun `a read request is dropped without answering`() {
        val output = filter.filter("\u001b]52;c;?\u0007rest")

        assertEquals("rest", output)
        assertTrue(copied.isEmpty())
    }

    @Test
    fun `other escape sequences pass through unchanged`() {
        val text = "\u001b[31mred\u001b[0m \u001b]0;title\u0007 \u001b]5x"

        assertEquals(text, filter.filter(text))
        assertTrue(copied.isEmpty())
    }

    @Test
    fun `an escape at the end of a read waits for the next one`() {
        assertEquals("text", filter.filter("text\u001b"))
        assertEquals("\u001b[1mbold", filter.filter("[1mbold"))
    }

    @Test
    fun `an endless copy is dropped rather than kept in memory`() {
        filter.filter("\u001b]52;c;")
        repeat(5) { filter.filter("A".repeat(1024 * 1024)) }
        val output = filter.filter("\u0007after")

        assertEquals("after", output)
        assertTrue(copied.isEmpty())
    }
}
