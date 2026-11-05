package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.settings.theme.BuiltinThemes
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.UserTheme
import com.sshellspace.feature.terminal.ui.toTerminalColors

/** Common themes that are always on offer, using BossTerm's palettes for them. */
val BuiltInTerminalThemes: List<TerminalColors> = listOf(
    BuiltinThemes.CATPPUCCIN_MOCHA,
    BuiltinThemes.DRACULA,
    BuiltinThemes.GRUVBOX_DARK,
    BuiltinThemes.MONOKAI,
    BuiltinThemes.NORD,
    BuiltinThemes.ONE_DARK,
    BuiltinThemes.SOLARIZED_DARK,
    BuiltinThemes.SOLARIZED_LIGHT,
    BuiltinThemes.TOKYO_NIGHT,
).map { it.toTerminalColors() }

/**
 * Everything the terminal can be set to, each under the name that selects it. A theme's name is its identity, so an
 * imported theme named like a built-in one takes its place.
 */
fun terminalThemeChoices(imported: List<TerminalColors>, userThemes: List<UserTheme>): List<TerminalColors> =
    (imported + userThemes.map { it.terminal.copy(name = it.name) } + BuiltInTerminalThemes).distinctBy { it.name }
