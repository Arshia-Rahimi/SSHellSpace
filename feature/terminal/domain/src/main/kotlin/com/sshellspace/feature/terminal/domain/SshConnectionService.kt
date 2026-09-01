package com.sshellspace.feature.terminal.domain

import kotlinx.coroutines.flow.Flow

interface SshConnectionService {
    suspend fun connect(config: SshConnectionConfig): Flow<SshConnectionStatus>
}

data class SshConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: SshAuthMethod,
    val knownHostsPath: String,
    val connectTimeoutSeconds: Long = 10,
    val authTimeoutSeconds: Long = 30,
    val onHostKeyPrompt: suspend (HostKeyPrompt) -> Boolean,
)

sealed class SshAuthMethod {
    data class PrivateKey(
        val path: String,
        val passphrase: CharArray? = null,
    ) : SshAuthMethod() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PrivateKey

            if (path != other.path) return false
            if (!passphrase.contentEquals(other.passphrase)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + (passphrase?.contentHashCode() ?: 0)
            return result
        }
    }

    data class Password(val password: CharArray) : SshAuthMethod() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Password

            return password.contentEquals(other.password)
        }

        override fun hashCode(): Int {
            return password.contentHashCode()
        }
    }

    data object Agent : SshAuthMethod()
}

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
    val isChangedKey: Boolean,
)

sealed class SshConnectionStatus {
    data class Success(val session: ShellSession.Ssh) : SshConnectionStatus()
    data class HostKeyRejected(val prompt: HostKeyPrompt) : SshConnectionStatus()
    data class AuthFailed(val reason: String) : SshConnectionStatus()
    data class ConnectFailed(val cause: Throwable) : SshConnectionStatus()
}
