package com.sshellspace.feature.terminal

import com.sshellspace.feature.terminal.screen.TerminalTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val TerminalModule = module {
    factory(named(TerminalTab.qualifier)) { TerminalTabViewModel() }
}
