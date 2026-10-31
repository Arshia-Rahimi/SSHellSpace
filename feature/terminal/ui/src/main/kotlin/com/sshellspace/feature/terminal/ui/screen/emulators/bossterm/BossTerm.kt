package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.getPlatformServices
import ai.rever.bossterm.compose.settings.TerminalSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
    command: String,
    terminalState: EmbeddableTerminalState,
    initialCommand: String? = null,
    onExit: ((Int) -> Unit)? = null,
    platformServices: PlatformServices = getPlatformServices(),
) {
    EmbeddableTerminal(
        settings = TerminalSettings(),
        command = command,
        initialCommand = initialCommand,
        onExit = onExit,
        state = terminalState,
        modifier = modifier,
        platformServices = platformServices,
    )
}
