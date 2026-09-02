package com.sshellspace.core.domain.models

import kotlin.uuid.Uuid
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

typealias KeyPair = Pair<PrivateKey, PublicKey>
typealias PrivateKey = String
typealias PublicKey = String

sealed interface SshKey {
    val key: PrivateKey
    val passphrase: String?
    val algorithm: Algorithm

    data class Vault(
        override val key: PrivateKey,
        override val algorithm: Algorithm,
    ) : SshKey {
        override val passphrase: String? = null
    }

    data class Device(
        override val passphrase: String?,
        override val algorithm: Algorithm,
        val path: String,
    ) : SshKey {
        override val key: PrivateKey
            get() = SystemFileSystem.source(Path(path)).use { it.toString() }
    }
}

data class SshKeyInfo(
    val id: Uuid,
    val name: String,
    val algorithm: Algorithm,
    val type: SshKeyType,
)

enum class SshKeyType {
    DEVICE,
    VAULT,
}

enum class Algorithm(
    val type: String,
) {
    RSA("ssh-rsa"),
    ED25519("ssh-ed25519"),
}
