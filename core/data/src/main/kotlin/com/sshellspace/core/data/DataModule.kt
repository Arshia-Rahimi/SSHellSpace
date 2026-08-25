package com.sshellspace.core.data

import com.sshellspace.core.data.impl.CorePreferencesRepositoryImpl
import com.sshellspace.core.data.impl.RgbaRepositoryImpl
import com.sshellspace.core.data.impl.SqlDelightPreferencesDatasource
import com.sshellspace.core.data.impl.SqlDelightPreferencesRepository
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.PreferencesRepository
import com.sshellspace.core.domain.repositories.RgbaRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val DataModule = module {
    single<SqlDelightPreferencesDatasource>() bind PreferencesDatasource::class
    single<SqlDelightPreferencesRepository>() bind PreferencesRepository::class
    single<CorePreferencesRepositoryImpl>() bind CorePreferencesRepository::class
    single<RgbaRepositoryImpl>() bind RgbaRepository::class
}
