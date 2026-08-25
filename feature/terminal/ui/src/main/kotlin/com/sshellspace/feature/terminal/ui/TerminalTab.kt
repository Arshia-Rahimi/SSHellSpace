package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabInstance
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel

val TerminalTab = Tab(
    qualifier = "TerminalTab",
    viewModelKClass = TerminalTabViewModel::class,
)

data class TerminalTabInstance(
    val shellType: String,
): TabInstance {
    override val tab: Tab = TerminalTab
}
