package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabItemFactory
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.terminal.domain.models.SftpParams
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.ui.screen.TerminalTabScreen
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import com.sshellspace.feature.terminal.ui.screen.sftp.SftpBrowserScreen
import com.sshellspace.feature.terminal.ui.screen.sftp.SftpBrowserViewModel
import com.sshellspace.feature.terminal.ui.screen.sftp.sftpTabTitle
import com.sshellspace.feature.terminal.ui.screen.ssh.SshTerminalTabScreen
import com.sshellspace.feature.terminal.ui.screen.ssh.SshTerminalTabViewModel
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

object SshTerminalTab : TabType

val SshTerminalTabFactory = TabItemFactory<SshTerminalParams> { parameters ->
    TabItem(
        type = SshTerminalTab,
        initialTitle = parameters.title,
        contentProvider = { item ->
            val viewModel: SshTerminalTabViewModel = koinViewModel {
                parametersOf(item as TabController, parameters)
            }
            SshTerminalTabScreen(viewModel)
        }
    )
}

object SftpTab : TabType

val SftpTabFactory = TabItemFactory<SftpParams> { parameters ->
    TabItem(
        type = SftpTab,
        initialTitle = sftpTabTitle(parameters.title),
        contentProvider = { item ->
            val viewModel: SftpBrowserViewModel = koinViewModel {
                parametersOf(item as TabController, parameters)
            }
            SftpBrowserScreen(viewModel)
        }
    )
}
