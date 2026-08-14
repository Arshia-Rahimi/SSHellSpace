package com.github.arshia_rahimi.core.data

import com.github.arshia_rahimi.core.model.Theme
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val theme: Flow<Theme>

    suspend fun setTheme(theme: Theme)
}
