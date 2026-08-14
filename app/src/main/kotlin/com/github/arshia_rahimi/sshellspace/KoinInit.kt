package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.data.DataModule
import com.github.arshia_rahimi.core.database.DatabaseModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            DatabaseModule,
            DataModule,
        )
    }
}
