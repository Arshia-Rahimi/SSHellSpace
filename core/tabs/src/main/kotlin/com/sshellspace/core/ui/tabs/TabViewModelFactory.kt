package com.sshellspace.core.ui.tabs

import androidx.lifecycle.ViewModel
import org.koin.core.Koin
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind

context(module: Module)
inline fun <reified T : ViewModel> TabType.registerViewModel(
    noinline definition: Definition<T>,
) = with(module) {
    if (isSingleInstance) single<T>(named(qualifier), definition = definition)
    else viewModel<T>(named(qualifier), definition = definition)
}
