package com.sshellspace.feature.terminal.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class TerminalPreferences(
    val defaultTerminalEmulator: TerminalEmulator = TerminalEmulator.BossTerm,
    val defaultShell: Shell = Shell(name = "", isDeviceDefault = true),
    /** The imported theme whose terminal colors to use. Null follows the app theme. */
    val terminalThemeName: String? = null,
)

@Serializable
data class UpdateTerminalPreferences(
    val defaultTerminalEmulator: TerminalEmulator? = null,
    val defaultShell: Shell? = null,
)
