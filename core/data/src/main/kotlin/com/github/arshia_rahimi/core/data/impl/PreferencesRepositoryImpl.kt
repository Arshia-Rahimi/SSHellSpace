package com.github.arshia_rahimi.core.data.impl

import com.github.arshia_rahimi.core.data.PreferencesRepository
import com.github.arshia_rahimi.core.database.PreferencesDatasource
import com.github.arshia_rahimi.core.model.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class PreferencesRepositoryImpl(
    private val preferencesDatasource: PreferencesDatasource,
) : PreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val preferences = preferencesDatasource.preferences
        .shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    override val theme = preferences.map { it.theme }

    override suspend fun setTheme(theme: Theme) {
        preferences.first().copy(theme = theme).let {
            preferencesDatasource.updatePreferences(it)
        }
    }
}
