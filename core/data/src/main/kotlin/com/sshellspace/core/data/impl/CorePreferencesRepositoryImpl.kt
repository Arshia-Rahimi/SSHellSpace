package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class CorePreferencesRepositoryImpl(
    private val preferencesRepository: PreferencesRepository,
) : CorePreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val preference = preferencesRepository.get(CorePreference::class)

    override fun getTheme() = preference
        .map { it.themePreference }
        .stateIn(scope, SharingStarted.WhileSubscribed(), ThemePreference.SYSTEM_DEFAULT)

    override suspend fun setTheme(theme: ThemePreference) {
        preferencesRepository.set(preference.first().copy(themePreference = theme))
    }
}
