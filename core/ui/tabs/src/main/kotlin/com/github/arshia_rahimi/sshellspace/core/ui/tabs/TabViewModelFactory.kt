package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import org.koin.core.Koin
import org.koin.core.qualifier.named

interface TabViewModelFactory {
    fun create(tabInstance: TabInstance): TabViewModel
}

class KoinTabViewModelFactory(
    private val koin: Koin,
) : TabViewModelFactory {
    override fun create(tabInstance: TabInstance): TabViewModel =
        koin.get(tabInstance.tab.viewModelKClass, named(tabInstance.tab.qualifier))
}
