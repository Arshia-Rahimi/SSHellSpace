package com.sshellspace.core.ui.tabs

import org.koin.core.Koin
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.plugin.module.dsl.create

interface TabViewModelFactory {
    fun create(tabInstance: TabInstance): TabViewModel
}

internal class KoinTabViewModelFactory(
    private val koin: Koin,
) : TabViewModelFactory {
    override fun create(tabInstance: TabInstance): TabViewModel =
        koin.get<TabViewModel>(named(tabInstance.tab.qualifier))
}

context(module: Module)
inline fun <reified T : TabViewModel> Tab.registerViewModel(
    isSingleton: Boolean = false,
    noinline definition: Definition<T>,
) = with(module) {
    if (isSingleton) single<T>(named(qualifier), definition = definition)
    else factory<T>(named(qualifier), definition = definition)
} bind TabViewModel::class
