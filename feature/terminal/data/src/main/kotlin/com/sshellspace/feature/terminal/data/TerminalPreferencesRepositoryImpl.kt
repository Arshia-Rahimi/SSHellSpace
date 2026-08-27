package com.sshellspace.feature.terminal.data

import com.sshellspace.core.domain.repositories.PreferencesRepository
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import com.sshellspace.feature.terminal.domain.models.UpdateTerminalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TerminalPreferencesRepositoryImpl(
    private val preferencesRepository: PreferencesRepository,
) : TerminalPreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun get(): StateFlow<TerminalPreferences> =
        preferencesRepository.get(TerminalPreferences::class)
            .stateIn(scope, SharingStarted.Eagerly, TerminalPreferences())

    override suspend fun set(value: UpdateTerminalPreferences) {
        var newValue = get().value
        if (value.defaultTerminalEmulator != null) {
            newValue = newValue.copy(defaultTerminalEmulator = value.defaultTerminalEmulator!!)
        }
        if (value.defaultShell != null) {
            newValue = newValue.copy(defaultShell = value.defaultShell!!)
        }
        preferencesRepository.set(newValue)
    }
}
