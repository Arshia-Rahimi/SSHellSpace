package com.sshellspace.core.domain.models

import java.io.File
import kotlin.uuid.Uuid

typealias KeyPair = Pair<PrivateKey, PublicKey>
typealias PrivateKey = String
typealias PublicKey = String

sealed interface SshKeySecret {
    val privateKey: PrivateKey

    data class Vault(
        override val privateKey: PrivateKey,
    ) : SshKeySecret {
        override fun toString() = "Vault(privateKey=***)"
    }

    data class Device(
        val path: String,
        val passphrase: String?,
    ) : SshKeySecret {
        override val privateKey: PrivateKey
            get() = File(path).readText()

        override fun toString() = "Device(path=$path, passphrase=${if (passphrase == null) "null" else "***"})"
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
    val defaultKeySize: Int,
    val minKeySize: Int,
) {
    RSA("ssh-rsa", defaultKeySize = 3072, minKeySize = 2048),
    Ed25519("ssh-ed25519", defaultKeySize = 256, minKeySize = 256),
}
