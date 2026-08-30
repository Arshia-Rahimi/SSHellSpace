package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.StoredPreference
import kotlinx.coroutines.flow.Flow

interface PreferencesDatasource {
    fun observeAll(): Flow<List<StoredPreference>>
    suspend fun set(key: String, value: String)
}
