package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.settings.theme.ColorPaletteManager
import ai.rever.bossterm.compose.settings.theme.ThemeManager
import ai.rever.bossterm.compose.util.ColorUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.composeunstyled.theme.Theme
import com.composeunstyled.theme.ThemeToken
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.UserTheme
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.info
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.success
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.toColorHex
import com.sshellspace.core.ui.designsystem.theme.warning
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.ui.toBossTermTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val FOLLOW_APP_THEME_NAME = "SSHellSpace"
private const val DARK_LUMINANCE_THRESHOLD = 0.5f
private const val BRIGHT_MIX = 0.25f
private const val SELECTION_ALPHA = 0.35f

/** How much of the scheme's color goes into the terminal's text, enough to see which scheme is on. */
private const val TEXT_TINT = 0.35f
private const val NEUTRAL_ANSI_TINT = 0.2f
private const val TINT_STEP = 0.05f

/** WCAG's enhanced level. Terminals are read for long stretches, and this is the color most of their text uses. */
private const val MIN_TEXT_CONTRAST = 7f
private const val CONTRAST_OFFSET = 0.05f

private val AppTokens = listOf(background, textPrimary, primary, onPrimary, error, success, warning, info)

/** The ANSI colors the app tokens have no equivalent for. */
private data class NeutralAnsi(
    val black: Color,
    val brightBlack: Color,
    val white: Color,
    val brightWhite: Color,
    val purple: Color,
    val cyan: Color,
)

private val DarkAnsi = NeutralAnsi(
    black = Color(0xFF484F58),
    brightBlack = Color(0xFF6E7681),
    white = Color(0xFFB1BAC4),
    brightWhite = Color(0xFFF0F6FC),
    purple = Color(0xFFBC8CFF),
    cyan = Color(0xFF39C5CF),
)

private val LightAnsi = NeutralAnsi(
    black = Color(0xFF24292F),
    brightBlack = Color(0xFF57606A),
    white = Color(0xFF6E7781),
    brightWhite = Color(0xFF8C959F),
    purple = Color(0xFF8250DF),
    cyan = Color(0xFF1B7C83),
)

/**
 * Keeps BossTerm's colors in line with the user's choice while a terminal is on screen.
 *
 * The colors go through BossTerm's global [ThemeManager] because that is what its renderer reads, so an open terminal
 * changes straight away. The settings passed to `EmbeddableTerminal` only matter when its session is created.
 */
@Composable
internal fun ApplyTerminalTheme() {
    val corePreferences = koinInject<CorePreferencesRepository>()
    val terminalPreferences = koinInject<TerminalPreferencesRepository>()
    val tokens = Theme[colors]
    val appColors by rememberUpdatedState(deriveTerminalColors(AppTokens.associateWith { tokens[it] }))

    LaunchedEffect(Unit) {
        combine(
            corePreferences.getTerminalThemes(),
            corePreferences.getUserThemes(),
            corePreferences.getActiveUserTheme(),
            terminalPreferences.get(),
            snapshotFlow { appColors },
        ) { imported, userThemes, activeTheme, preferences, followed ->
            val choices = terminalThemeChoices(imported, userThemes)
            resolveTerminalColors(preferences.terminalThemeName, choices, activeTheme, followed)
        }
            .distinctUntilChanged()
            .collect { withContext(Dispatchers.IO) { applyToBossTerm(it) } }
    }
}

/**
 * The colors to show: the theme picked for the terminal, else the active imported theme's terminal colors, else colors
 * derived from a built-in scheme. A pick whose theme has since been removed counts as following the app.
 */
internal fun resolveTerminalColors(
    terminalThemeName: String?,
    choices: List<TerminalColors>,
    activeTheme: UserTheme?,
    appColors: TerminalColors,
): TerminalColors =
    choices.firstOrNull { it.name == terminalThemeName } ?: activeTheme?.terminal ?: appColors

/** [text] with as much of [brand] as it can take, up to [TEXT_TINT], while staying easy to read on [page]. */
internal fun tintedTextColor(text: Color, brand: Color, page: Color): Color {
    var amount = TEXT_TINT
    while (amount > 0f) {
        val tinted = lerp(text, brand, amount)
        if (contrast(tinted, page) >= MIN_TEXT_CONTRAST) return tinted
        amount -= TINT_STEP
    }
    return text
}

private fun contrast(first: Color, second: Color): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

/**
 * Builds a terminal palette from the app's color tokens, so a built-in scheme also themes the terminal. The schemes all
 * share one text token, so the scheme's color is mixed into the text to make it follow. Red, green, yellow and blue keep
 * their usual hues, since programs rely on them to mean things like an error.
 */
internal fun deriveTerminalColors(tokens: Map<ThemeToken<Color>, Color>): TerminalColors {
    val backgroundColor = tokens.getValue(background)
    val brand = tokens.getValue(primary)
    val foregroundColor = tintedTextColor(tokens.getValue(textPrimary), brand, backgroundColor)
    val isDark = backgroundColor.luminance() < DARK_LUMINANCE_THRESHOLD
    val ansi = if (isDark) DarkAnsi else LightAnsi
    val brighten = { color: Color -> lerp(color, if (isDark) Color.White else Color.Black, BRIGHT_MIX) }
    val red = tokens.getValue(error)
    val green = tokens.getValue(success)
    val yellow = tokens.getValue(warning)
    val blue = tokens.getValue(info)
    val neutral = { color: Color -> lerp(color, brand, NEUTRAL_ANSI_TINT).toColorHex() }

    return TerminalColors(
        name = FOLLOW_APP_THEME_NAME,
        background = backgroundColor.toColorHex(),
        foreground = foregroundColor.toColorHex(),
        cursor = brand.toColorHex(),
        cursorText = tokens.getValue(onPrimary).toColorHex(),
        selection = brand.copy(alpha = SELECTION_ALPHA).compositeOver(backgroundColor).toColorHex(),
        selectionText = foregroundColor.toColorHex(),
        searchMatch = yellow.toColorHex(),
        hyperlink = brand.toColorHex(),
        black = neutral(ansi.black),
        brightBlack = neutral(ansi.brightBlack),
        red = red.toColorHex(),
        brightRed = brighten(red).toColorHex(),
        green = green.toColorHex(),
        brightGreen = brighten(green).toColorHex(),
        yellow = yellow.toColorHex(),
        brightYellow = brighten(yellow).toColorHex(),
        blue = blue.toColorHex(),
        brightBlue = brighten(blue).toColorHex(),
        purple = ansi.purple.toColorHex(),
        brightPurple = brighten(ansi.purple).toColorHex(),
        cyan = ansi.cyan.toColorHex(),
        brightCyan = brighten(ansi.cyan).toColorHex(),
        white = neutral(ansi.white),
        brightWhite = neutral(ansi.brightWhite),
    )
}

/** Blocks on a settings file write inside BossTerm, so this belongs off the UI thread. */
private fun applyToBossTerm(colors: TerminalColors) {
    val theme = colors.toBossTermTheme()
    val themeManager = ThemeManager.instance
    if (themeManager.currentTheme.value != theme) {
        themeManager.applyTheme(theme)
        // applyTheme leaves the caches of colors solved against the old background in place.
        ColorUtils.invalidateColorCache()
    }
    // A palette left selected in ~/.bossterm/settings.json would override the theme's ANSI colors.
    val paletteManager = ColorPaletteManager.instance
    if (!paletteManager.isUsingThemePalette()) paletteManager.useThemePalette()
}
