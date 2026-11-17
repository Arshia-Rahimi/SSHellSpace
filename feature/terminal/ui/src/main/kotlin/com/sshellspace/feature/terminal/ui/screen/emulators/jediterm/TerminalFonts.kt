package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import kotlin.math.ceil
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface

/** Monospace fonts each desktop ships, best first. Skia falls back to its default font when none is installed. */
private val MonospaceFamilies = arrayOf<String?>(
    "Menlo", "SF Mono", "Monaco",
    "Cascadia Mono", "Consolas",
    "JetBrains Mono", "DejaVu Sans Mono", "Ubuntu Mono", "Liberation Mono", "Noto Sans Mono",
    "monospace",
)

/**
 * The terminal's fonts at [sizePx], and the size of one cell. Text goes straight to Skia rather than through Compose's
 * text layout, which is far slower for a screen redrawn on every change. Characters the font lacks, such as CJK or
 * emoji, are drawn in whichever installed font has them.
 */
internal class TerminalFonts(sizePx: Float) {
    private val fontManager = FontMgr.default
    private val fonts = listOf(FontStyle.NORMAL, FontStyle.BOLD, FontStyle.ITALIC, FontStyle.BOLD_ITALIC)
        .map { style -> makeFont(fontManager.matchFamiliesStyle(MonospaceFamilies, style), sizePx) }
    private val fallbacks = HashMap<Long, Font?>()
    private val regular = fonts[0]

    val cellWidth: Float = regular.measureTextWidth("M")
    val ascent: Float = -regular.metrics.ascent
    val cellHeight: Float = ceil(regular.metrics.descent - regular.metrics.ascent + regular.metrics.leading)
    val underlineOffset: Float = regular.metrics.underlinePosition ?: (regular.metrics.descent / 2)
    val underlineThickness: Float = regular.metrics.underlineThickness ?: 1f

    fun font(bold: Boolean, italic: Boolean): Font = fonts[(if (bold) 1 else 0) + (if (italic) 2 else 0)]

    /** A font that can draw [codePoint], or null if no installed font can. */
    fun fontFor(codePoint: Int, bold: Boolean, italic: Boolean): Font? {
        val font = font(bold, italic)
        if (font.getUTF32Glyph(codePoint) != 0.toShort()) return font
        val key = (codePoint.toLong() shl 2) or (if (bold) 1L else 0L) or (if (italic) 2L else 0L)
        return fallbacks.getOrPut(key) {
            fontManager.matchFamilyStyleCharacter(null, styleOf(bold, italic), null, codePoint)
                ?.let { makeFont(it, font.size) }
        }
    }

    private fun styleOf(bold: Boolean, italic: Boolean) = when {
        bold && italic -> FontStyle.BOLD_ITALIC
        bold -> FontStyle.BOLD
        italic -> FontStyle.ITALIC
        else -> FontStyle.NORMAL
    }
}

private fun makeFont(typeface: Typeface?, sizePx: Float): Font = Font(typeface, sizePx).apply {
    edging = FontEdging.SUBPIXEL_ANTI_ALIAS
    hinting = FontHinting.SLIGHT
    isSubpixel = true
}
