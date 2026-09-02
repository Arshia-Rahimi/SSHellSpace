package com.sshellspace.feature.terminal.domain.models

import kotlin.uuid.Uuid
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable

sealed interface SshCredentials {
    val name: String
    val key: SshKey
    val passphrase: String
    val algorithm: Algorithm

    data class Vault(
        override val name: String,
        override val key: SshKey,
        override val passphrase: String,
        override val algorithm: Algorithm,
        val id: Uuid,
    ) : SshCredentials

    data class Device(
        override val name: String,
        override val passphrase: String,
        override val algorithm: Algorithm,
        val path: String,
    ) : SshCredentials {
        override val key: SshKey
            get() = SshKey(
                passphrase = passphrase,
                algorithm = algorithm,
                privateKey = SystemFileSystem.source(Path(path))
                    .use { it.toString() },
            )
    }
}

@Serializable
data class SshKey(
    val algorithm: Algorithm,
    val passphrase: String?,
    val privateKey: String,
)

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
