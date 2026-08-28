package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabInitializer
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.TerminalTabScreen
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object TerminalTab : TabType

fun terminalTabInitializer(
    shell: Shell,
) = TabInitializer {
    TabItem(
        type = TerminalTab,
        initialTitle = shell.name,
        contentProvider = { item ->
            val viewModel: TerminalTabViewModel = koinViewModel { parametersOf(item as TabController) }
            TerminalTabScreen(viewModel)
        }
    )
}
