package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

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

class BossTermThemeTest {

    private val appColors = terminalColors("app")
    private val nord = userTheme("nord")
    private val dracula = userTheme("dracula")

    @Test
    fun `follows the app when nothing is picked and no imported theme is active`() {
        assertEquals(appColors, resolveTerminalColors(null, listOf(nord), null, appColors))
    }

    @Test
    fun `following the app uses the active imported theme's terminal colors`() {
        assertEquals(nord.terminal, resolveTerminalColors(null, listOf(nord), nord, appColors))
    }

    @Test
    fun `a picked theme wins over the active one`() {
        assertEquals(dracula.terminal, resolveTerminalColors("dracula", listOf(nord, dracula), nord, appColors))
    }

    @Test
    fun `a picked theme that was removed falls back to following the app`() {
        assertEquals(appColors, resolveTerminalColors("gone", listOf(nord), null, appColors))
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
