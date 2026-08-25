package com.sshellspace.feature.terminal.ui

import com.sshellspace.feature.terminal.ui.screen.TerminalTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val TerminalUiModule = module {
    factory(named(TerminalTab.qualifier)) { TerminalTabViewModel() }
}
