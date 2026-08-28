package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabItemFactory
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.ui.screen.TerminalTabScreen
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object TerminalTab : TabType

val TerminalTabFactory = TabItemFactory<TerminalParams> { parameters ->
    TabItem(
        type = TerminalTab,
        initialTitle = parameters.shell.name,
        contentProvider = { item ->
            val viewModel: TerminalTabViewModel = koinViewModel {
                parametersOf(item as TabController, parameters.shell)
            }
            TerminalTabScreen(viewModel)
        }
    )
}
