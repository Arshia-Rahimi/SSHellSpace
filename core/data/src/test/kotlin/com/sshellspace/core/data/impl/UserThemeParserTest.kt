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

    private val windowsTerminalScheme = """
        {
          "name": "Campbell",
          "cursorColor": "#FFFFFF",
          "selectionBackground": "#FFFFFF",
          "background": "#0C0C0C",
          "foreground": "#CCCCCC",
          "black": "#0C0C0C",
          "blue": "#0037DA",
          "cyan": "#3A96DD",
          "green": "#13A10E",
          "purple": "#881798",
          "red": "#C50F1F",
          "white": "#CCCCCC",
          "yellow": "#C19C00",
          "brightBlack": "#767676",
          "brightBlue": "#3B78FF",
          "brightCyan": "#61D6D6",
          "brightGreen": "#16C60C",
          "brightPurple": "#B4009E",
          "brightRed": "#E74856",
          "brightWhite": "#F2F2F2",
          "brightYellow": "#F9F1A5"
        }
    """.trimIndent()

    @Test
    fun `reads a Windows Terminal scheme and fills in what it leaves out`() {
        val theme = parseTerminalTheme(windowsTerminalScheme, "file")

        assertEquals("Campbell", theme.name)
        assertEquals("0xFF0C0C0C", theme.background)
        assertEquals("0xFFFFFFFF", theme.cursor)
        assertEquals("0xFF0C0C0C", theme.cursorText)
        assertEquals("0xFFFFFFFF", theme.selection)
        assertEquals("0xFFCCCCCC", theme.selectionText)
        assertEquals("0xFFC19C00", theme.searchMatch)
        assertEquals("0xFF0037DA", theme.hyperlink)
        assertEquals("0xFFB4009E", theme.brightPurple)
    }

    @Test
    fun `reads the terminal section of a whole app theme`() {
        val theme = parseTerminalTheme(example, "file")

        assertEquals("Example", theme.name)
        assertEquals("0xFFBC8CFF", theme.purple)
        assertEquals(parseUserTheme(example).terminal, theme)
    }

    @Test
    fun `a file without a name takes the one given for it`() {
        val theme = parseTerminalTheme(windowsTerminalScheme.replace("\"name\": \"Campbell\",", ""), "my-colors")

        assertEquals("my-colors", theme.name)
    }

    @Test
    fun `a terminal theme's name can't carry control characters or run long`() {
        val hostile = windowsTerminalScheme.replace("Campbell", "a\\u001Bb" + "x".repeat(200))

        val name = parseTerminalTheme(hostile, "file").name

        assertEquals(64, name.length)
        assertEquals("ab", name.take(2))
    }

    @Test
    fun `reports which terminal color is missing`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseTerminalTheme(windowsTerminalScheme.replace("\"brightRed\"", "\"other\""), "file")
        }

        assertContains(error.message.orEmpty(), "bright_red")
    }

    @Test
    fun `reports which terminal color can't be read`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseTerminalTheme(windowsTerminalScheme.replace("\"#C50F1F\"", "\"red\""), "file")
        }

        assertContains(error.message.orEmpty(), "red")
    }

    @Test
    fun `refuses terminal text that can't be read`() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseTerminalTheme(windowsTerminalScheme.replace("\"foreground\": \"#CCCCCC\"", "\"foreground\": \"#0C0C0D\""), "file")
        }

        assertContains(error.message.orEmpty(), "foreground")
    }

    @Test
    fun `refuses a see-through terminal background`() {
        assertFailsWith<IllegalArgumentException> {
            parseTerminalTheme(windowsTerminalScheme.replace("\"background\": \"#0C0C0C\"", "\"background\": \"#800C0C0C\""), "file")
        }
    }

    @Test
    fun `refuses a terminal theme that isn't json`() {
        assertFailsWith<IllegalArgumentException> { parseTerminalTheme("not json {", "file") }
    }
}
