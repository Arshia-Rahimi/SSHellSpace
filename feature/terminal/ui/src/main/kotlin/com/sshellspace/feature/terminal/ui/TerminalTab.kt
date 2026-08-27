package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.feature.terminal.domain.models.Shell

fun terminalTab()
data class TerminalTab(
    val shell: Shell,
) : Tab(
    initialTitle = shell.name,
    initialIcon = null,
)
