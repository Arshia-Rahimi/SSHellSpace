package com.github.arshia_rahimi.core.database

import com.github.arshia_rahimi.core.model.Preferences
import kotlinx.coroutines.flow.Flow

interface PreferencesDatasource {
    val preferences: Flow<Preferences>

    suspend fun updatePreferences(preferences: Preferences)
}
