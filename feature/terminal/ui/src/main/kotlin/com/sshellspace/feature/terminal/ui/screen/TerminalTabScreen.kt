package com.sshellspace.feature.terminal.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshellspace.feature.terminal.ui.screen.components.BossTerm
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TerminalTabScreen() {
    val viewModel = koinViewModel<TerminalTabViewModel>()

    BossTerm(Modifier.fillMaxSize())
}
