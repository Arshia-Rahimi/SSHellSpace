package com.sshellspace.core.data

import com.sshellspace.core.data.models.Preferences
import kotlinx.coroutines.flow.Flow

interface PreferencesDatasource {
    fun getPreferences(): Flow<Preferences>
    suspend fun updatePreferences(preferences: Preferences)
}
