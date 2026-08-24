package com.sshellspace.core.data

import com.sshellspace.core.data.impl.PreferencesRepositoryImpl
import com.sshellspace.core.data.impl.SqldelightPreferencesDataSource
import com.sshellspace.core.data.repositories.PreferencesRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val DataModule = module {
    single<SqldelightPreferencesDataSource>() bind PreferencesDatasource::class
    single<PreferencesRepositoryImpl>() bind PreferencesRepository::class
}
