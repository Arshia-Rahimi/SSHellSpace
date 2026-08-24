package com.sshellspace.core.data.impl

import com.sshellspace.core.data.PreferencesDatasource
import com.sshellspace.core.data.models.Preferences
import com.sshellspace.core.data.models.Theme
import com.sshellspace.core.data.repositories.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PreferencesRepositoryImpl(
    private val preferencesDatasource: PreferencesDatasource,
) : PreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val preferences = preferencesDatasource.getPreferences()
        .stateIn(scope, SharingStarted.Eagerly, Preferences())

    override fun getTheme() = preferences.map { it.theme }

    override suspend fun setTheme(theme: Theme) {
        preferences.first().copy(theme = theme).let {
            preferencesDatasource.updatePreferences(it)
        }
    }
}
