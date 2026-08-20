package com.github.arshia_rahimi.core.database

import com.github.arshia_rahimi.core.data.models.Preferences
import kotlinx.coroutines.flow.Flow

interface PreferencesDatasource {
    fun getPreferences(): Flow<Preferences>
    suspend fun updatePreferences(preferences: Preferences)
}
