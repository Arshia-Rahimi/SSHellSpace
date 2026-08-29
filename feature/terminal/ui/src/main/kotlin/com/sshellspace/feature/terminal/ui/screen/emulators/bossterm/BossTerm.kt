package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
    command: String,
    terminalState: EmbeddableTerminalState,
) {
    EmbeddableTerminal(
        command = command,
        state = terminalState,
        modifier = modifier,
    )
}
