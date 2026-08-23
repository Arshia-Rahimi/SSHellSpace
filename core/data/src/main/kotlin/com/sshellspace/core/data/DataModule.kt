package com.sshellspace.core.data

import com.sshellspace.core.data.repositories.PreferencesRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val DataModule = module {
    single<PreferencesRepositoryImpl>() bind PreferencesRepository::class
}
