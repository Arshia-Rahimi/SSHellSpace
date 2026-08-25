package com.sshellspace.feature.terminal.ui

import com.sshellspace.core.ui.tabs.registerViewModel
import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create

val TerminalUiModule = module {
    TerminalTab.registerViewModel { create(::TerminalTabViewModel) }
}
