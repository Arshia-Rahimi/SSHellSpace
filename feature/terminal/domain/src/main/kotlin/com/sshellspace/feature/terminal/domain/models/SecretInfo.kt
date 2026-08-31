package com.sshellspace.feature.terminal.domain.models

@JvmInline
value class Secret(val value: String)

data class SecretInfo(
    val profileId: String,
    val secretType: SecretType,
)

enum class SecretType {
    Password,
}
