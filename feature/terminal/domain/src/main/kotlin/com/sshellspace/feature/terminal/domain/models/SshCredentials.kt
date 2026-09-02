package com.sshellspace.feature.terminal.domain.models

import kotlin.uuid.Uuid
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

sealed interface SshCredentials {
    val name: String
    val key: PrivateKey
    val passphrase: String?
    val algorithm: Algorithm

    data class Vault(
        override val name: String,
        override val key: PrivateKey,
        override val algorithm: Algorithm,
        val id: Uuid,
    ) : SshCredentials {
        override val passphrase: String? = null
    }

    data class Device(
        override val name: String,
        override val passphrase: String?,
        override val algorithm: Algorithm,
        val path: String,
    ) : SshCredentials {
        override val key: PrivateKey
            get() = SystemFileSystem.source(Path(path)).use { it.toString() }
    }
}

typealias KeyPair = Pair<PrivateKey, PublicKey>
typealias PrivateKey = String
typealias PublicKey = String

enum class Algorithm(
    val type: String,
) {
    RSA("ssh-rsa"),
    ED25519("ssh-ed25519"),
}
