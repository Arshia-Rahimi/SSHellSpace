package com.github.arshia_rahimi.sshellspace.feature.terminal

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModelFactoryEntry
import com.github.arshia_rahimi.sshellspace.feature.terminal.screen.TerminalTabViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val TerminalModule = module {
    single(named<TerminalTab>()) {
        TabViewModelFactoryEntry<TerminalTab> { _ -> TerminalTabViewModel() }
    }
}
