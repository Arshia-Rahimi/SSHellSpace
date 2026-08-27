package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import com.sshellspace.feature.terminal.domain.models.UpdateTerminalPreferences
import kotlinx.coroutines.flow.StateFlow

interface TerminalPreferencesRepository {
    fun get(): StateFlow<TerminalPreferences>
    suspend fun set(value: UpdateTerminalPreferences)
}
