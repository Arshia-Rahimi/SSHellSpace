package com.sshellspace.core.data.impl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserThemeParserTest {

    private val example = checkNotNull(javaClass.getResource("/custom-theme.json")).readText()

    @Test
    fun `parses every section of the example theme`() {
        val theme = parseUserTheme(example)

        assertEquals("Example", theme.name)
        assertEquals("0xFF2DD4BF", theme.dark.primary)
        assertEquals("0xFF0D9488", theme.light.primary)
        assertEquals("0xFFBC8CFF", theme.terminal.purple)
        assertEquals("Example", theme.terminal.name)
    }

    @Test
    fun `six digit colors become opaque and eight digit colors keep their alpha`() {
        val theme = parseUserTheme(example)

        assertEquals("0xFF101418", theme.dark.background)
        assertEquals("0x99000000", theme.dark.scrim)
    }

    @Test
    fun `accepts colors already in the 0x form`() {
        val theme = parseUserTheme(example.replace("\"#2DD4BF\"", "\"0x802dd4bf\""))

        assertEquals("0x802DD4BF", theme.dark.primary)
    }

    @Test
    fun `reports which token holds an invalid color`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseUserTheme(example.replace("\"on_error\": \"#FFFFFF\"", "\"on_error\": \"white\""))
        }

        assertContains(error.message.orEmpty(), "dark.on_error")
    }

    @Test
    fun `fails when a token is missing`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseUserTheme(example.replace("\"bright_white\": \"#F0F6FC\"", "\"unknown\": \"#F0F6FC\""))
        }

        assertContains(error.message.orEmpty(), "bright_white")
    }

    @Test
    fun `fails when the terminal section is missing`() {
        val withoutTerminal = """{"name": "x", "dark": {}, "light": {}}"""

        assertFailsWith<IllegalArgumentException> { parseUserTheme(withoutTerminal) }
    }

    @Test
    fun `refuses text too faint to read`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseUserTheme(example.replace("\"text_primary\": \"#E6EDF3\"", "\"text_primary\": \"#101418\""))
        }

        assertContains(error.message.orEmpty(), "dark.text_primary")
    }

    @Test
    fun `refuses a transparent warning color`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseUserTheme(example.replace("\"error\": \"#F85149\"", "\"error\": \"#00F85149\""))
        }

        assertContains(error.message.orEmpty(), "dark.error")
    }

    @Test
    fun `fails on malformed json`() {
        assertFailsWith<IllegalArgumentException> { parseUserTheme("not json {") }
    }

    @Test
    fun `fails on a blank name`() {
        assertFailsWith<IllegalArgumentException> {
            parseUserTheme(example.replaceFirst("\"name\": \"Example\"", "\"name\": \"  \""))
        }
    }
}
