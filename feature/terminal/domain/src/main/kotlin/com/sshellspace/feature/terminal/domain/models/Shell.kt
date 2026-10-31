package com.sshellspace.feature.terminal.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Shell(
    val name: String,
    val command: String? = null,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val isDeviceDefault: Boolean = false,
)

/**
 * The concrete shell to launch for the [preferred] default: itself when it names a command,
 * otherwise the device's default shell from this list of discovered shells.
 */
fun List<Shell>.resolveDefault(preferred: Shell): Shell {
    if (preferred.command != null) return preferred
    return firstOrNull { it.isDeviceDefault } ?: firstOrNull() ?: preferred
}
