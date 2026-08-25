package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabInstance
import com.sshellspace.feature.terminal.domain.Shell
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel

object TerminalTab: Tab

data class TerminalTabInstance(
    val shell: Shell,
): TabInstance {
    override val tab: Tab = TerminalTab
}
