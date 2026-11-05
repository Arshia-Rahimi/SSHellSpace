package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.models.ColorHex
import com.sshellspace.core.domain.models.TerminalColors
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

/** The spelling Windows Terminal's scheme files use, which is how most downloadable terminal themes come. */
private val TERMINAL_KEY_ALIASES = mapOf(
    "cursorColor" to "cursor",
    "cursorText" to "cursor_text",
    "selectionBackground" to "selection",
    "selectionForeground" to "selection_text",
    "brightBlack" to "bright_black",
    "brightRed" to "bright_red",
    "brightGreen" to "bright_green",
    "brightYellow" to "bright_yellow",
    "brightBlue" to "bright_blue",
    "brightPurple" to "bright_purple",
    "brightCyan" to "bright_cyan",
    "brightWhite" to "bright_white",
)
private const val MAX_NAME_LENGTH = 64

/**
 * Parses a terminal theme file: this app's `terminal` tokens, a Windows Terminal scheme, or a whole app theme, of which
 * only the terminal part is used. Colors only the app uses are optional, and come from the required ones.
 *
 * @throws IllegalArgumentException when the JSON is malformed, a color is missing, or a color can't be read.
 */
internal fun parseTerminalTheme(json: String, fallbackName: String): TerminalColors {
    val root = JsonSerializer.parseToJsonElement(json) as? JsonObject
        ?: throw IllegalArgumentException("The file doesn't contain a JSON object.")
    val section = root["terminal"] as? JsonObject ?: root
    val tokens = section.mapKeys { (key, _) -> TERMINAL_KEY_ALIASES[key] ?: key }

    fun optional(token: String): ColorHex? = tokens[token]?.let { value ->
        requireNotNull((value as? JsonPrimitive)?.contentOrNull?.let(::normalizeColor)) {
            "$token: $value isn't a color. Use #RRGGBB, #AARRGGBB or 0xAARRGGBB."
        }
    }
    fun required(token: String): ColorHex = requireNotNull(optional(token)) { "The file has no $token color." }

    val background = required("background")
    val foreground = required("foreground")
    val yellow = required("yellow")
    val blue = required("blue")
    val brightBlack = required("bright_black")
    requireOpaque("background", background)
    requireOpaque("foreground", foreground)
    requireContrast("foreground", foreground, background, MIN_UI_CONTRAST)

    return TerminalColors(
        name = terminalThemeName(section, root, fallbackName),
        background = background,
        foreground = foreground,
        cursor = optional("cursor") ?: foreground,
        cursorText = optional("cursor_text") ?: background,
        selection = optional("selection") ?: brightBlack,
        selectionText = optional("selection_text") ?: foreground,
        searchMatch = optional("search_match") ?: yellow,
        hyperlink = optional("hyperlink") ?: blue,
        black = required("black"),
        brightBlack = brightBlack,
        red = required("red"),
        brightRed = required("bright_red"),
        green = required("green"),
        brightGreen = required("bright_green"),
        yellow = yellow,
        brightYellow = required("bright_yellow"),
        blue = blue,
        brightBlue = required("bright_blue"),
        purple = required("purple"),
        brightPurple = required("bright_purple"),
        cyan = required("cyan"),
        brightCyan = required("bright_cyan"),
        white = required("white"),
        brightWhite = required("bright_white"),
    )
}

/** Comes from a file someone else may have written, and ends up on a button in Settings. */
private fun terminalThemeName(section: JsonObject, root: JsonObject, fallbackName: String): String {
    val given = listOf(section, root).firstNotNullOfOrNull { (it[NAME_KEY] as? JsonPrimitive)?.contentOrNull }
    val name = (given?.takeIf { it.isNotBlank() } ?: fallbackName)
        .filterNot { it.isISOControl() }
        .trim()
        .take(MAX_NAME_LENGTH)
    require(name.isNotEmpty()) { "The theme's name is empty." }
    return name
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
