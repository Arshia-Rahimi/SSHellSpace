package com.github.arshia_rahimi.sshellspace.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(DIModules)
    }
}
