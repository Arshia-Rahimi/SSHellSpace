package com.sshellspace.core.data.impl

import com.sshellspace.core.common.firstIsInstanceOrNull
import com.sshellspace.core.data.PreferencesDatasource
import com.sshellspace.core.domain.models.Preference
import com.sshellspace.core.domain.repositories.PreferencesRepository
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

class SqlDelightPreferencesRepository(
    private val preferencesDatasource: PreferencesDatasource,
) : PreferencesRepository {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val preferences: SharedFlow<List<Preference>> = preferencesDatasource.get()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override fun <T : Preference> get(klass: KClass<T>): Flow<T> = preferences
        .mapNotNull { it.firstIsInstanceOrNull(klass) }

    override suspend fun <T : Preference> set(value: T) {
        val currentPreference = preferences.first().firstIsInstanceOrNull(value::class)
        if (currentPreference == null) preferencesDatasource.insert(value)
        else preferencesDatasource.update(value)
    }
}
