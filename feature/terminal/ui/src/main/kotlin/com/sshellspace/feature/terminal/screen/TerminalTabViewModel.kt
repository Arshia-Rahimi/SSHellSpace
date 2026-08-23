package com.sshellspace.feature.terminal.screen

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.tabs.TabViewModel

class TerminalTabViewModel : TabViewModel(
    initialTitle = "Terminal",
    initialIcon = null,
) {
    @Composable
    override fun screen() = TerminalTabScreen()
}
