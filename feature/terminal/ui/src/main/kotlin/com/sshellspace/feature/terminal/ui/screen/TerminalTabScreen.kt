package com.sshellspace.feature.terminal.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sshellspace.feature.terminal.ui.screen.components.BossTerm

@Composable
fun TerminalTabScreen() {
    BossTerm(Modifier.fillMaxSize())
}
