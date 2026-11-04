package com.sshellspace.feature.terminal.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class TerminalPreferences(
    val defaultTerminalEmulator: TerminalEmulator = TerminalEmulator.BossTerm,
    val defaultShell: Shell = Shell(name = "", isDeviceDefault = true),
    /** The imported theme whose terminal colors to use. Null follows the app theme. */
    val terminalThemeName: String? = null,
    /** Keep trying to reconnect an SSH tab whose connection dropped. */
    val autoReconnect: Boolean = true,
    /** Reopen a vault's tabs the next time it unlocks. */
    val restoreTabs: Boolean = true,
    /** Keep terminals and port forwards running while the vault is locked, instead of closing them. */
    val keepSessionsWhenLocked: Boolean = true,
)

@Serializable
data class UpdateTerminalPreferences(
    val defaultTerminalEmulator: TerminalEmulator? = null,
    val defaultShell: Shell? = null,
    val autoReconnect: Boolean? = null,
    val restoreTabs: Boolean? = null,
    val keepSessionsWhenLocked: Boolean? = null,
)
