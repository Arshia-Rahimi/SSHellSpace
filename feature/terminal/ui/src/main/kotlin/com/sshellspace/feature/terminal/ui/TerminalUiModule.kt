package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.SftpParams
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import com.sshellspace.feature.terminal.ui.screen.sftp.SftpBrowserViewModel
import com.sshellspace.feature.terminal.ui.screen.ssh.SshTerminalTabViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val TerminalUiModule = module {
    viewModel { (tabController: TabController, shell: Shell) ->
        TerminalTabViewModel(tabController, shell, get())
    }
    viewModel { (tabController: TabController, params: SshTerminalParams) ->
        SshTerminalTabViewModel(tabController, params, get(), get(), get())
    }
    viewModel { (tabController: TabController, params: SftpParams) ->
        SftpBrowserViewModel(tabController, params, get(), get())
    }
}
