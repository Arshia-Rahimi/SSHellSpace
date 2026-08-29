package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.Preference
import kotlinx.serialization.Serializable

@Serializable
data class TerminalPreferences(
    val defaultTerminalEmulator: TerminalEmulator = TerminalEmulator.BossTerm,
    val defaultShell: Shell = Shell.DeviceDefault,
): Preference

@Serializable
data class UpdateTerminalPreferences(
    val defaultTerminalEmulator: TerminalEmulator? = null,
    val defaultShell: Shell? = null,
)
