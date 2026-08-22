package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import org.koin.core.Koin
import org.koin.core.qualifier.named

interface TabViewModelFactory {
    fun create(qualifier: TabQualifier): TabViewModel
}

@JvmInline
value class TabQualifier(val value: String)

class KoinTabViewModelFactory(
    private val koin: Koin,
) : TabViewModelFactory {
    override fun create(qualifier: TabQualifier): TabViewModel =
        koin.get<TabViewModel>(named(qualifier.value))
}
