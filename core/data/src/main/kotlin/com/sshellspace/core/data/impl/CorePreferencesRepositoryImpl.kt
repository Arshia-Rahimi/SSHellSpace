package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

internal class CorePreferencesRepositoryImpl(
    private val datasource: PreferencesDatasource,
) : CorePreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val key = CorePreference::class.qualifiedName!!

    private val preference: StateFlow<CorePreference> = datasource.observeAll()
        .map { rows -> rows.firstOrNull { it.key == key }?.value }
        .map { stored -> stored?.let { JsonSerializer.decodeFromString(CorePreference.serializer(), it) } }
        .mapNotNull { it }
        .stateIn(scope, SharingStarted.Eagerly, CorePreference())

    override fun getTheme() = preference
        .map { it.themePreference }

    override suspend fun setTheme(theme: ThemePreference) {
        val current = preference.value
        val updated = current.copy(themePreference = theme)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }
}
