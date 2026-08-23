package com.sshellspace.feature.terminal.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sshellspace.feature.terminal.screen.components.XTermTerminal

@Composable
fun TerminalTabScreen() {
    XTermTerminal(Modifier.fillMaxSize())
}
