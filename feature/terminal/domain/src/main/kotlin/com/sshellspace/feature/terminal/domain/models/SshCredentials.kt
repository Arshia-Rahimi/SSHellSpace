package com.sshellspace.feature.terminal.domain.models

import java.io.File
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable

@Serializable
data class SshCredentials(
    val name: String,
    val key: SshKey,
)

@Serializable
sealed interface SshKey {
    val algorithm: Algorithm
    val passphrase: String?
    val privateKey: String
}

@Serializable
data class DeviceSshKey(
    override val algorithm: Algorithm,
    override val passphrase: String? = null,
    val privateKeyFilePath: String,
): SshKey {
    override val privateKey: String
        get() = SystemFileSystem.source(Path(privateKeyFilePath))
            .use { it.toString() }
}

@Serializable
data class VaultSshKey(
    override val algorithm: Algorithm,
    override val privateKey: String,
): SshKey {
    override val passphrase: String? = null
}

data class KeyPair(
    val private: String,
    val public: String,
)

enum class Algorithm(
    val type: String,
) {
    RSA("ssh-rsa"),
    ED25519("ssh-ed25519"),
}
