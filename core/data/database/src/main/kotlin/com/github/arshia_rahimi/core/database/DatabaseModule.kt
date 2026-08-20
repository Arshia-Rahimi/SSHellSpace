package com.github.arshia_rahimi.core.database

import com.github.arshia_rahimi.core.database.impl.SqldelightPreferencesDataSource
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.single

val DatabaseModule = module {
    single { create(::createSSHellSpaceDatabase) }
    single<SqldelightPreferencesDataSource>() bind PreferencesDatasource::class
}
