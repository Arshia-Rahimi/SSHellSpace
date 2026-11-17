package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.util.CharUtils
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint

private const val CURSOR_THICKNESS = 2f
private const val UNFOCUSED_CURSOR_STROKE = 1f
private const val ASCII_PRINTABLE_START = 0x20
private const val ASCII_PRINTABLE_END = 0x7e

/**
 * Draws a [JediTermSession]'s screen with Skia, one line at a time. Runs of plain text in one style are drawn in one
 * call, and anything else (wide characters, emoji, characters the font lacks) a character at a time.
 */
internal class TerminalRenderer(private val fonts: TerminalFonts) {
    private val paint = Paint().apply { isAntiAlias = true }
    private val run = StringBuilder()
    private var runStart = 0
    private var runColor = Color.Unspecified
    private var runFont: Font? = null

    fun draw(scope: DrawScope, session: JediTermSession, palette: TerminalPalette, focused: Boolean) = with(scope) {
        drawRect(palette.background)
        val rows = (size.height / fonts.cellHeight).toInt() + 1
        session.locked {
            val buffer = session.textBuffer
            val offset = session.scrollOffset.coerceIn(0, buffer.historyLinesCount)
            val selection = session.selection
            for (row in 0 until rows) {
                val index = row - offset
                if (index >= buffer.height) break
                drawLine(buffer.getLine(index), index, row * fonts.cellHeight, palette, selection)
            }
            val cursorRow = session.cursorY + offset
            if (session.isCursorVisible && cursorRow in 0 until rows && session.cursorY < buffer.height) {
                drawCursor(session, buffer.getLine(session.cursorY), cursorRow, palette, focused)
            }
        }
    }

    private fun DrawScope.drawLine(
        line: TerminalLine,
        index: Int,
        top: Float,
        palette: TerminalPalette,
        selection: Selection?,
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        val baseline = top + fonts.ascent
        var column = 0
        line.forEachEntry { entry ->
            val style = entry.style
            val colors = palette.colorsOf(style)
            val font = fonts.font(style.hasOption(TextStyle.Option.BOLD), style.hasOption(TextStyle.Option.ITALIC))
            val underlined = style.hasOption(TextStyle.Option.UNDERLINED)
            val text = entry.text
            var i = 0
            while (i < text.length) {
                val cell = column + i
                val selected = selection?.contains(cell, index) == true
                val background = if (selected) palette.selection else colors.background
                val foreground = if (selected) palette.selectionText else colors.foreground
                if (background != palette.background) {
                    drawRect(background, Offset(cell * fonts.cellWidth, top), Size(fonts.cellWidth, fonts.cellHeight))
                }
                if (underlined) drawUnderline(cell, baseline, foreground)
                i += drawCharacter(canvas, text, i, cell, baseline, foreground, font, style)
            }
            column += text.length
        }
        flush(canvas, baseline)
    }

    /** Draws the character at [i] of [text], or adds it to the current run. Returns how many chars it took. */
    @Suppress("LongParameterList")
    private fun drawCharacter(
        canvas: Canvas,
        text: CharSequence,
        i: Int,
        cell: Int,
        baseline: Float,
        color: Color,
        font: Font,
        style: TextStyle,
    ): Int {
        val char = text[i]
        val isPair = Character.isHighSurrogate(char) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])
        val codePoint = if (isPair) Character.toCodePoint(char, text[i + 1]) else char.code
        val taken = if (isPair) 2 else 1
        when {
            char == CharUtils.DWC || char == CharUtils.NUL_CHAR || char == ' ' -> flush(canvas, baseline)
            codePoint in ASCII_PRINTABLE_START..ASCII_PRINTABLE_END -> {
                if (run.isNotEmpty() && (runColor != color || runFont !== font || runStart + run.length != cell)) {
                    flush(canvas, baseline)
                }
                if (run.isEmpty()) {
                    runStart = cell
                    runColor = color
                    runFont = font
                }
                run.append(char)
            }
            else -> {
                flush(canvas, baseline)
                val bold = style.hasOption(TextStyle.Option.BOLD)
                val italic = style.hasOption(TextStyle.Option.ITALIC)
                val glyphFont = fonts.fontFor(codePoint, bold, italic) ?: font
                paint.color = color.toArgb()
                canvas.drawString(String(Character.toChars(codePoint)), cell * fonts.cellWidth, baseline, glyphFont, paint)
            }
        }
        return taken
    }

    private fun flush(canvas: Canvas, baseline: Float) {
        val font = runFont
        if (run.isEmpty() || font == null) return
        paint.color = runColor.toArgb()
        canvas.drawString(run.toString(), runStart * fonts.cellWidth, baseline, font, paint)
        run.clear()
    }

    private fun DrawScope.drawUnderline(cell: Int, baseline: Float, color: Color) {
        drawRect(
            color,
            Offset(cell * fonts.cellWidth, baseline + fonts.underlineOffset),
            Size(fonts.cellWidth, fonts.underlineThickness),
        )
    }

    private fun DrawScope.drawCursor(
        session: JediTermSession,
        line: TerminalLine,
        row: Int,
        palette: TerminalPalette,
        focused: Boolean,
    ) {
        val left = session.cursorX * fonts.cellWidth
        val top = row * fonts.cellHeight
        val cell = Size(fonts.cellWidth, fonts.cellHeight)
        when (session.cursorShape) {
            CursorShape.BLINK_UNDERLINE, CursorShape.STEADY_UNDERLINE -> drawRect(
                palette.cursor,
                Offset(left, top + fonts.cellHeight - CURSOR_THICKNESS),
                Size(fonts.cellWidth, CURSOR_THICKNESS),
            )
            CursorShape.BLINK_VERTICAL_BAR, CursorShape.STEADY_VERTICAL_BAR ->
                drawRect(palette.cursor, Offset(left, top), Size(CURSOR_THICKNESS, fonts.cellHeight))
            else -> if (focused) {
                drawRect(palette.cursor, Offset(left, top), cell)
                val char = if (session.cursorX < line.length()) line.charAt(session.cursorX) else ' '
                if (char.code in ASCII_PRINTABLE_START..ASCII_PRINTABLE_END && char != ' ') {
                    val style = line.getStyleAt(session.cursorX)
                    val font = fonts.font(
                        style?.hasOption(TextStyle.Option.BOLD) == true,
                        style?.hasOption(TextStyle.Option.ITALIC) == true,
                    )
                    paint.color = palette.cursorText.toArgb()
                    drawContext.canvas.nativeCanvas.drawString(char.toString(), left, top + fonts.ascent, font, paint)
                }
            } else {
                drawRect(palette.cursor, Offset(left, top), cell, style = Stroke(UNFOCUSED_CURSOR_STROKE))
            }
        }
    }
}
