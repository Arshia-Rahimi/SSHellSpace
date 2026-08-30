package com.sshellspace.core.data

import kotlinx.coroutines.flow.Flow

data class StoredPreference(
    val key: String,
    val value: String,
)

interface PreferencesDatasource {
    fun observeAll(): Flow<List<StoredPreference>>
    suspend fun set(key: String, value: String)
}
