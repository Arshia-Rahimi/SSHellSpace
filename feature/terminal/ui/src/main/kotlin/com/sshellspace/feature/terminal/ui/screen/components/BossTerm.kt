package com.sshellspace.feature.terminal.ui.screen.components

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
) {
    val terminalState = rememberEmbeddableTerminalState(autoDispose = false)
    EmbeddableTerminal(
        state = terminalState,
        modifier = modifier,
    )
}
