package com.github.arshia_rahimi.sshellspace.feature.terminal

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.sshellspace.core.tabs.TabViewModel

class TerminalTabViewModel: TabViewModel(
    initialTitle = "Vault",
    initialIcon = null,
) {
    @Composable
    override fun screen() = TerminalTabScreen()
}
