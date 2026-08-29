package com.sshellspace.feature.terminal.ui.screen.components

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
    terminalState: EmbeddableTerminalState,
) {
    EmbeddableTerminal(
        state = terminalState,
        modifier = modifier,
    )
}
