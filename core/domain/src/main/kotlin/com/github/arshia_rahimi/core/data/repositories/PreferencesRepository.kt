package com.github.arshia_rahimi.core.data.repositories

import com.github.arshia_rahimi.core.data.models.Theme
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun getTheme(): Flow<Theme>
    suspend fun setTheme(theme: Theme)
}
