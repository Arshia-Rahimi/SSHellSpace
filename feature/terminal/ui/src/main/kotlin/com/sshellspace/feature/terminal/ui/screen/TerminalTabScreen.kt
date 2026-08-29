package com.sshellspace.feature.terminal.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sshellspace.feature.terminal.ui.screen.components.BossTerm

@Composable
fun TerminalTabScreen(
    viewModel: TerminalTabViewModel,
) {
    BossTerm(
        modifier = Modifier.fillMaxSize(),
        terminalState = viewModel.terminalState,
    )
}
