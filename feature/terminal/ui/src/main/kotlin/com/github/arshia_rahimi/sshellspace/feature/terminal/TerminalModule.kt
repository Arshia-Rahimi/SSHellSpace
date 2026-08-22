package com.github.arshia_rahimi.sshellspace.feature.terminal

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel
import com.github.arshia_rahimi.sshellspace.feature.terminal.screen.TerminalTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val TerminalModule = module {
    factory(named(TerminalTabQualifier.value)) { TerminalTabViewModel() } bind TabViewModel::class
}
