package com.sshellspace.core.data

import com.sshellspace.core.domain.models.Preference
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface PreferencesDatasource {
    fun get(): Flow<List<Preference>>
    suspend fun insert(value: @Serializable Preference)
    suspend fun update(value: @Serializable Preference)
}
