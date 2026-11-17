package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPaletteImpl
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.ui.designsystem.theme.toColor

private const val ANSI_COLORS = 16
private const val DIM_ALPHA = 0.6f

/** The colors one cell is drawn in. */
internal data class CellColors(val foreground: Color, val background: Color)

/** [TerminalColors] ready to draw with, and what JediTerm's color numbers mean in them. */
internal class TerminalPalette(
    val foreground: Color,
    val background: Color,
    val cursor: Color,
    val cursorText: Color,
    val selection: Color,
    val selectionText: Color,
    /** The 16 ANSI colors: black, red, green, yellow, blue, magenta, cyan and white, then their bright versions. */
    private val ansi: List<Color>,
) {
    fun colorsOf(style: TextStyle): CellColors {
        var foreground = resolve(style.foreground, foreground)
        var background = resolve(style.background, background)
        if (style.hasOption(TextStyle.Option.INVERSE)) foreground = background.also { background = foreground }
        if (style.hasOption(TextStyle.Option.DIM)) foreground = foreground.copy(alpha = DIM_ALPHA).compositeOver(background)
        if (style.hasOption(TextStyle.Option.HIDDEN)) foreground = background
        return CellColors(foreground, background)
    }

    private fun resolve(color: TerminalColor?, fallback: Color): Color = when {
        color == null -> fallback
        color.isIndexed -> ansi.getOrElse(color.colorIndex) { fallback }
        else -> color.toColor().let { Color(it.red, it.green, it.blue, it.alpha) }
    }

    companion object {
        /** Until the view has the theme's colors: xterm's. */
        val Default = TerminalPalette(
            foreground = Color.LightGray,
            background = Color.Black,
            cursor = Color.LightGray,
            cursorText = Color.Black,
            selection = Color.DarkGray,
            selectionText = Color.White,
            ansi = (0 until ANSI_COLORS).map { index ->
                ColorPaletteImpl.XTERM_PALETTE.getForeground(TerminalColor.index(index))
                    .let { Color(it.red, it.green, it.blue) }
            },
        )

        fun of(colors: TerminalColors) = TerminalPalette(
            foreground = colors.foreground.toColor(),
            background = colors.background.toColor(),
            cursor = colors.cursor.toColor(),
            cursorText = colors.cursorText.toColor(),
            selection = colors.selection.toColor(),
            selectionText = colors.selectionText.toColor(),
            ansi = with(colors) {
                listOf(
                    black, red, green, yellow, blue, purple, cyan, white,
                    brightBlack, brightRed, brightGreen, brightYellow, brightBlue, brightPurple, brightCyan, brightWhite,
                )
            }.map { it.toColor() },
        )
    }
}
