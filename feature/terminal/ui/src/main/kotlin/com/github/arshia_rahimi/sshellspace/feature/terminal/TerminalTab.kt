package com.github.arshia_rahimi.sshellspace.feature.terminal

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.Tab
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabInstance
import com.github.arshia_rahimi.sshellspace.feature.terminal.screen.TerminalTabViewModel

val TerminalTab = Tab(
    qualifier = "TerminalTab",
    viewModelKClass = TerminalTabViewModel::class,
)

data class TerminalTabInstance(
    val shellType: String,
): TabInstance {
    override val tab: Tab = TerminalTab
}
