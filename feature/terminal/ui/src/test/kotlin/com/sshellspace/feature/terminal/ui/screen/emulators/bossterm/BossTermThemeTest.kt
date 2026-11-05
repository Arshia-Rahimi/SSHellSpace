package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePalette
import com.sshellspace.core.domain.models.UserTheme
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.toColor
import com.sshellspace.core.ui.designsystem.theme.toColorHex
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BossTermThemeTest {

    private val appColors = terminalColors("app")
    private val nord = userTheme("nord")
    private val dracula = userTheme("dracula")

    private val choices = terminalThemeChoices(emptyList(), listOf(nord, dracula))

    @Test
    fun `follows the app when nothing is picked and no imported theme is active`() {
        assertEquals(appColors, resolveTerminalColors(null, choices, null, appColors))
    }

    @Test
    fun `following the app uses the active imported theme's terminal colors`() {
        assertEquals(nord.terminal, resolveTerminalColors(null, choices, nord, appColors))
    }

    @Test
    fun `a picked theme wins over the active one`() {
        assertEquals(dracula.terminal, resolveTerminalColors("dracula", choices, nord, appColors))
    }

    @Test
    fun `a picked theme that was removed falls back to following the app`() {
        assertEquals(appColors, resolveTerminalColors("gone", choices, null, appColors))
    }

    @Test
    fun `built-in themes can be picked`() {
        val picked = resolveTerminalColors("Tokyo Night", choices, nord, appColors)

        assertEquals("Tokyo Night", picked.name)
        assertEquals(BuiltInTerminalThemes.single { it.name == "Tokyo Night" }, picked)
    }

    @Test
    fun `an imported terminal theme takes the place of a built-in one with its name`() {
        val mine = terminalColors("Nord")

        val found = terminalThemeChoices(listOf(mine), emptyList()).filter { it.name == "Nord" }

        assertEquals(listOf(mine), found)
    }

    @Test
    fun `an app theme's terminal colors are offered under the app theme's name`() {
        val theme = userTheme("Mine").copy(terminal = terminalColors("something else"))

        assertTrue(terminalThemeChoices(emptyList(), listOf(theme)).any { it.name == "Mine" })
    }

    @Test
    fun `every built-in theme is complete, opaque and readable`() {
        assertEquals(9, BuiltInTerminalThemes.size)
        BuiltInTerminalThemes.forEach { theme ->
            assertEquals(1f, theme.background.toColor().alpha, theme.name)
            assertEquals(1f, theme.foreground.toColor().alpha, theme.name)
            assertTrue(contrastOf(theme.foreground.toColor(), theme.background.toColor()) >= 3f, theme.name)
        }
    }

    @Test
    fun `text follows the color scheme, not just the cursor`() {
        val foregrounds = AppColorScheme.entries.map { deriveTerminalColors(it.colors(isDark = true)).foreground }

        assertEquals(AppColorScheme.entries.size, foregrounds.toSet().size)
    }

    @Test
    fun `tinted text stays easy to read in every scheme, light and dark`() {
        AppColorScheme.entries.forEach { scheme ->
            listOf(true, false).forEach { dark ->
                val derived = deriveTerminalColors(scheme.colors(dark))
                val contrast = contrastOf(derived.foreground.toColor(), derived.background.toColor())
                assertTrue(contrast >= 7f, "${scheme.label} dark=$dark has contrast $contrast")
            }
        }
    }

    @Test
    fun `text that can't take the tint keeps its own color`() {
        val text = Color(0xFF777777)

        assertEquals(text, tintedTextColor(text, Color(0xFF888888), Color(0xFF666666)))
    }

    @Test
    fun `colors that carry meaning keep their hue`() {
        val blue = AppColorScheme.Default.colors(isDark = true)
        val rose = AppColorScheme.Rose.colors(isDark = true)

        assertEquals(deriveTerminalColors(blue).green, deriveTerminalColors(rose).green)
        assertEquals(deriveTerminalColors(blue).red, deriveTerminalColors(rose).red)
    }

    @Test
    fun `derived colors come from the app tokens`() {
        val tokens = AppColorScheme.Emerald.colors(isDark = true)

        val derived = deriveTerminalColors(tokens)

        assertEquals(tokens.getValue(background).toColorHex(), derived.background)
        assertEquals(tokens.getValue(primary).toColorHex(), derived.cursor)
        assertEquals(tokens.getValue(error).toColorHex(), derived.red)
    }

    @Test
    fun `derived colors differ between light and dark`() {
        val dark = deriveTerminalColors(AppColorScheme.Default.colors(isDark = true))
        val light = deriveTerminalColors(AppColorScheme.Default.colors(isDark = false))

        assertNotEquals(dark.black, light.black)
    }

    @Test
    fun `derived selection is opaque`() {
        val derived = deriveTerminalColors(AppColorScheme.Default.colors(isDark = true))

        assertEquals(1f, derived.selection.toColor().alpha)
    }

    @Test
    fun `hex conversion round trips and keeps alpha`() {
        assertEquals("0x80123456", "0x80123456".toColor().toColorHex())
        assertEquals(0x80123456.toInt(), "0x80123456".toColor().toArgb())
    }

    private fun contrastOf(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun userTheme(name: String) = UserTheme(
        name = name,
        dark = mockk<ThemePalette>(),
        light = mockk<ThemePalette>(),
        terminal = terminalColors(name),
    )

    private fun terminalColors(name: String) = TerminalColors(
        name = name,
        background = "0xFF000000",
        foreground = "0xFF000000",
        cursor = "0xFF000000",
        cursorText = "0xFF000000",
        selection = "0xFF000000",
        selectionText = "0xFF000000",
        searchMatch = "0xFF000000",
        hyperlink = "0xFF000000",
        black = "0xFF000000",
        brightBlack = "0xFF000000",
        red = "0xFF000000",
        brightRed = "0xFF000000",
        green = "0xFF000000",
        brightGreen = "0xFF000000",
        yellow = "0xFF000000",
        brightYellow = "0xFF000000",
        blue = "0xFF000000",
        brightBlue = "0xFF000000",
        purple = "0xFF000000",
        brightPurple = "0xFF000000",
        cyan = "0xFF000000",
        brightCyan = "0xFF000000",
        white = "0xFF000000",
        brightWhite = "0xFF000000",
    )
}
