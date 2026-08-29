package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.Preference
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface PreferencesRepository {
    fun <T: Preference> get(klass: KClass<T>): Flow<T>
    suspend fun <T: Preference> set(value: T)
}
