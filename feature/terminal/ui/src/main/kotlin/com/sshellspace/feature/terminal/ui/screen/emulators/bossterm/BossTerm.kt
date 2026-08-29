package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.settings.TerminalSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
    command: String,
    terminalState: EmbeddableTerminalState,
) {
    EmbeddableTerminal(
        settings = TerminalSettings(),
        command = command,
        state = terminalState,
        modifier = modifier,
    )
}
