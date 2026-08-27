package com.sshellspace.feature.terminal.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Shell(
    val name: String,
    val path: String,
) {
    companion object {
        val DeviceDefault = Shell("", "")
    }
}
