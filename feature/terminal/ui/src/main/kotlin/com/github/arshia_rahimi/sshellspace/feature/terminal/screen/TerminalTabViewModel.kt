package com.github.arshia_rahimi.sshellspace.feature.terminal.screen

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel

class TerminalTabViewModel : TabViewModel(
    initialTitle = "Vault",
    initialIcon = null,
) {
    @Composable
    override fun screen() = TerminalTabScreen()
}
