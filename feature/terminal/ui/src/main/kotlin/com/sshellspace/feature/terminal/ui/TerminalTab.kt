package com.sshellspace.feature.terminal.ui

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.TerminalTabScreen

object TerminalTabType : TabType

data class TerminalTab(
    val shell: Shell,
) : Tab(
    type = TerminalTabType,
    initialTitle = shell.name,
    initialIcon = null,
) {
    @Composable
    override fun screen() = TerminalTabScreen()
}
