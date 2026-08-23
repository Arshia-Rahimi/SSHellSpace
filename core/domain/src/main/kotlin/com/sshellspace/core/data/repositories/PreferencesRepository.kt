package com.sshellspace.core.data.repositories

import com.sshellspace.core.data.models.Theme
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun getTheme(): Flow<Theme>
    suspend fun setTheme(theme: Theme)
}
