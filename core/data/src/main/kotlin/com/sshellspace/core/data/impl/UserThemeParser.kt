package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.models.ColorHex
import com.sshellspace.core.domain.models.ThemePalette
import com.sshellspace.core.domain.models.UserTheme
import kotlin.math.pow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val COLOR_SECTIONS = setOf("dark", "light", "terminal")
private const val NAME_KEY = "name"
private const val RGB_LENGTH = 6
private const val MIN_TEXT_CONTRAST = 4.5
private const val MIN_UI_CONTRAST = 3.0
private const val CONTRAST_OFFSET = 0.05
private val COLOR_REGEX = Regex("^(?:#|0x)([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

/**
 * Parses an imported theme file. Every color is rewritten to `0xAARRGGBB`, the only form the app and BossTerm read
 * correctly: a six-digit value would otherwise be taken as fully transparent.
 *
 * @throws IllegalArgumentException when the JSON is malformed, a token is missing, or a color can't be read.
 */
internal fun parseUserTheme(json: String): UserTheme {
    val root = JsonSerializer.parseToJsonElement(json) as? JsonObject
        ?: throw IllegalArgumentException("The file doesn't contain a JSON object.")
    val normalized = JsonObject(
        root.mapValues { (key, value) ->
            if (key in COLOR_SECTIONS && value is JsonObject) normalizeColors(key, value) else value
        },
    )
    val theme = JsonSerializer.decodeFromJsonElement(UserTheme.serializer(), normalized)
    val name = theme.name.trim()
    require(name.isNotEmpty()) { "The theme's name is empty." }
    requireReadable("dark", theme.dark)
    requireReadable("light", theme.light)
    requireOpaque("terminal.background", theme.terminal.background)
    requireOpaque("terminal.foreground", theme.terminal.foreground)
    requireContrast("terminal.foreground", theme.terminal.foreground, theme.terminal.background, MIN_UI_CONTRAST)
    return theme.copy(name = name)
}

/** A theme mustn't be able to hide what's on screen, least of all the host key warning. */
private fun requireReadable(section: String, palette: ThemePalette) {
    requireOpaque("$section.background", palette.background)
    requireOpaque("$section.surface", palette.surface)
    requireOpaque("$section.surface_elevated", palette.surfaceElevated)
    requireOpaque("$section.text_primary", palette.textPrimary)
    requireOpaque("$section.text_secondary", palette.textSecondary)
    requireOpaque("$section.error", palette.error)
    requireContrast("$section.text_primary", palette.textPrimary, palette.background, MIN_TEXT_CONTRAST)
    requireContrast("$section.text_primary", palette.textPrimary, palette.surface, MIN_TEXT_CONTRAST)
    requireContrast("$section.text_secondary", palette.textSecondary, palette.surface, MIN_UI_CONTRAST)
    requireContrast("$section.error", palette.error, palette.surface, MIN_UI_CONTRAST)
}

private fun requireOpaque(name: String, color: ColorHex) {
    require(color.startsWith("0xFF")) { "$name has to be fully opaque." }
}

private fun requireContrast(name: String, color: ColorHex, background: ColorHex, minimum: Double) {
    val lighter = maxOf(luminance(color), luminance(background))
    val darker = minOf(luminance(color), luminance(background))
    require((lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET) >= minimum) {
        "$name is too hard to read against its background."
    }
}

/** WCAG relative luminance of a `0xFFRRGGBB` color. */
private fun luminance(color: ColorHex): Double {
    val (red, green, blue) = listOf(4, 6, 8).map { start ->
        val channel = color.substring(start, start + 2).toInt(16) / 255.0
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private fun normalizeColors(section: String, colors: JsonObject) = JsonObject(
    colors.mapValues { (key, value) ->
        if (key == NAME_KEY) return@mapValues value
        val color = (value as? JsonPrimitive)?.contentOrNull?.let(::normalizeColor)
        requireNotNull(color) { "$section.$key: $value isn't a color. Use #RRGGBB, #AARRGGBB or 0xAARRGGBB." }
        JsonPrimitive(color)
    },
)

private fun normalizeColor(value: String): ColorHex? {
    val digits = COLOR_REGEX.matchEntire(value)?.groupValues?.get(1)?.uppercase() ?: return null
    return if (digits.length == RGB_LENGTH) "0xFF$digits" else "0x$digits"
}
