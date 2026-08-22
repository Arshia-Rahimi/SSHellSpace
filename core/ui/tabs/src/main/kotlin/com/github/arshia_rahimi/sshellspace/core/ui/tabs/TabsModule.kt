package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val TabsModule = module {
    single<KoinTabViewModelFactory>() bind TabViewModelFactory::class
}
