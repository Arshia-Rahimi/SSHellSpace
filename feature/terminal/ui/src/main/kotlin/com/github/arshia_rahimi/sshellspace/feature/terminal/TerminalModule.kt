package com.github.arshia_rahimi.sshellspace.feature.terminal

import com.github.arshia_rahimi.sshellspace.feature.terminal.api.TerminalTabViewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val TerminalModule = module {
    factory<TerminalTabViewModelImpl>()
}
