package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.ThemePreference
import kotlinx.coroutines.flow.StateFlow

interface CorePreferencesRepository {
    fun getTheme(): StateFlow<ThemePreference>
    suspend fun setTheme(theme: ThemePreference)
}
