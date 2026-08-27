package com.sshellspace.feature.terminal.ui

import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val TerminalUiModule = module {
    viewModel<TerminalTabViewModel>()
}
