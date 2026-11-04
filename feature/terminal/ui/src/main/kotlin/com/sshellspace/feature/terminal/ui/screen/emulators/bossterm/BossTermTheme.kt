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
            corePreferences.getUserThemes(),
            corePreferences.getActiveUserTheme(),
            terminalPreferences.get(),
            snapshotFlow { appColors },
        ) { userThemes, activeTheme, preferences, followed ->
            resolveTerminalColors(preferences.terminalThemeName, userThemes, activeTheme, followed)
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
    userThemes: List<UserTheme>,
    activeTheme: UserTheme?,
    appColors: TerminalColors,
): TerminalColors {
    val picked = userThemes.firstOrNull { it.name == terminalThemeName }
    return (picked ?: activeTheme)?.terminal ?: appColors
}

/** Builds a terminal palette from the app's color tokens, so a built-in scheme also themes the terminal. */
internal fun deriveTerminalColors(tokens: Map<ThemeToken<Color>, Color>): TerminalColors {
    val backgroundColor = tokens.getValue(background)
    val foregroundColor = tokens.getValue(textPrimary)
    val isDark = backgroundColor.luminance() < DARK_LUMINANCE_THRESHOLD
    val ansi = if (isDark) DarkAnsi else LightAnsi
    val brighten = { color: Color -> lerp(color, if (isDark) Color.White else Color.Black, BRIGHT_MIX) }
    val red = tokens.getValue(error)
    val green = tokens.getValue(success)
    val yellow = tokens.getValue(warning)
    val blue = tokens.getValue(info)

    return TerminalColors(
        name = FOLLOW_APP_THEME_NAME,
        background = backgroundColor.toColorHex(),
        foreground = foregroundColor.toColorHex(),
        cursor = tokens.getValue(primary).toColorHex(),
        cursorText = tokens.getValue(onPrimary).toColorHex(),
        selection = tokens.getValue(primary).copy(alpha = SELECTION_ALPHA).compositeOver(backgroundColor).toColorHex(),
        selectionText = foregroundColor.toColorHex(),
        searchMatch = yellow.toColorHex(),
        hyperlink = blue.toColorHex(),
        black = ansi.black.toColorHex(),
        brightBlack = ansi.brightBlack.toColorHex(),
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
        white = ansi.white.toColorHex(),
        brightWhite = ansi.brightWhite.toColorHex(),
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
