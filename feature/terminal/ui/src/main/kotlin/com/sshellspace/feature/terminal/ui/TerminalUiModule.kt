package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val TerminalUiModule = module {
    viewModel { (tabController: TabController, shell: Shell) ->
        TerminalTabViewModel(tabController, shell, get())
    }
}
