package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.ThemePreference
import kotlinx.coroutines.flow.Flow

interface CorePreferencesRepository {
    fun getTheme(): Flow<ThemePreference>
    suspend fun setTheme(theme: ThemePreference)
}
