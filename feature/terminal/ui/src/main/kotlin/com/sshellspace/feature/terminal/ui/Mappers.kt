package com.sshellspace.feature.terminal.ui

import ai.rever.bossterm.compose.settings.theme.Theme
import com.sshellspace.core.domain.models.TerminalColors

fun TerminalColors.toBossTermTheme() = Theme(
    id = name,
    name = name,
    foreground = foreground,
    background = background,
    cursor = cursor,
    cursorText = cursorText,
    selection = selection,
    selectionText = selectionText,
    searchMatch = searchMatch,
    hyperlink = hyperlink,
    black = black,
    brightBlack = brightBlack,
    red = red,
    brightRed = brightRed,
    green = green,
    brightGreen = brightGreen,
    yellow = yellow,
    brightYellow = brightYellow,
    blue = blue,
    brightBlue = brightBlue,
    magenta = purple,
    brightMagenta = brightPurple,
    cyan = cyan,
    brightCyan = brightCyan,
    white = white,
    brightWhite = brightWhite,
)
