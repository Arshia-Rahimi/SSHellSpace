package com.sshellspace.core.data

import com.sshellspace.core.domain.models.Preference
import kotlinx.coroutines.flow.Flow

interface PreferencesDatasource {
    fun get(): Flow<List<Preference>>
    suspend fun insert(value: Preference)
    suspend fun update(value: Preference)
}
