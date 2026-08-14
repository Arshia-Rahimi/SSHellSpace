package com.github.arshia_rahimi.core.data

import com.github.arshia_rahimi.core.data.impl.PreferencesRepositoryImpl
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val DataModule = module {
    single<PreferencesRepositoryImpl>() bind PreferencesRepository::class
}
