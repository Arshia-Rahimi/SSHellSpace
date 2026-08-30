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
