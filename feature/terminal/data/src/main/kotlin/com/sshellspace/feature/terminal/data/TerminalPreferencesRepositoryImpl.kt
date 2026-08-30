package com.sshellspace.feature.terminal.data

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.data.PreferencesDatasource
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import com.sshellspace.feature.terminal.domain.models.UpdateTerminalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

class TerminalPreferencesRepositoryImpl(
    private val datasource: PreferencesDatasource,
) : TerminalPreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = TerminalPreferences::class.qualifiedName!!

    override fun get(): StateFlow<TerminalPreferences> = datasource.observeAll()
        .map { rows -> rows.firstOrNull { it.key == key }?.value }
        .map { stored -> stored?.let { JsonSerializer.decodeFromString(TerminalPreferences.serializer(), it) } }
        .mapNotNull { it }
        .stateIn(scope, SharingStarted.Eagerly, TerminalPreferences())

    override suspend fun set(value: UpdateTerminalPreferences) {
        var newValue = get().value
        if (value.defaultTerminalEmulator != null) {
            newValue = newValue.copy(defaultTerminalEmulator = value.defaultTerminalEmulator!!)
        }
        if (value.defaultShell != null) {
            newValue = newValue.copy(defaultShell = value.defaultShell!!)
        }
        datasource.set(key, JsonSerializer.encodeToString(TerminalPreferences.serializer(), newValue))
    }
}
