package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TerminalFontsTest {

    private val fonts = TerminalFonts(sizePx = 26f)

    @Test
    fun `cells have a size and every letter is as wide as one`() {
        assertTrue(fonts.cellWidth > 0f)
        assertTrue(fonts.cellHeight > fonts.cellWidth)
        assertEquals(fonts.cellWidth, fonts.font(bold = false, italic = false).measureTextWidth("i"))
    }

    @Test
    fun `plain text uses the terminal font`() {
        assertSame(fonts.font(bold = true, italic = false), fonts.fontFor('a'.code, bold = true, italic = false))
    }
}
