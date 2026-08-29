package com.sshellspace.feature.terminal.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TerminalTabScreen(
    viewModel: TerminalTabViewModel,
) {
    viewModel.terminal.content(Modifier.fillMaxSize())
}
