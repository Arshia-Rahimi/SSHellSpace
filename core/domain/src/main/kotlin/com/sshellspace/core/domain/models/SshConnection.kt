package com.sshellspace.core.domain.models

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A saved SSH host profile. */
data class SshConnection(
    val id: Uuid,
    val name: String,
    val host: String,
    val port: Int = DEFAULT_SSH_PORT,
    val username: String,
    val auth: SshConnectionAuth,
    val options: SshConnectionOptions = SshConnectionOptions(),
)

const val DEFAULT_SSH_PORT = 22

/**
 * How the client authenticates. Each variant maps to an Apache MINA sshd client auth method
 * (`password`, `publickey` or `keyboard-interactive`).
 */
@Serializable
sealed interface SshConnectionAuth {

    /** `password` auth. A null [password] means the user is asked for it on every connect. */
    @Serializable
    @SerialName("password")
    data class Password(val password: String? = null) : SshConnectionAuth {
        override fun toString() = "Password(***)"
    }

    /** `publickey` auth with a key from the SSH keys page (vault or device key). */
    @Serializable
    @SerialName("stored_key")
    data class StoredKey(val keyId: Uuid) : SshConnectionAuth

    /** `publickey` auth with a private key file on disk that isn't in the vault. */
    @Serializable
    @SerialName("key_file")
    data class KeyFile(
        val path: String,
        val passphrase: String? = null,
    ) : SshConnectionAuth {
        override fun toString() = "KeyFile(path=$path, passphrase=***)"
    }

    /** `publickey` auth with the identities held by the running SSH agent (`SSH_AUTH_SOCK`). */
    @Serializable
    @SerialName("agent")
    data object Agent : SshConnectionAuth

    /**
     * `keyboard-interactive` auth, for servers that ask one or more questions (OTP, 2FA, PAM).
     * [password] answers password prompts automatically; anything else is asked on connect.
     */
    @Serializable
    @SerialName("keyboard_interactive")
    data class KeyboardInteractive(val password: String? = null) : SshConnectionAuth {
        override fun toString() = "KeyboardInteractive(***)"
    }
}

@Serializable
data class SshConnectionOptions(
    /** Another saved connection to tunnel through (`ProxyJump`). */
    val jumpHostId: Uuid? = null,
    val connectTimeoutSeconds: Int = 10,
    /** Interval between keep-alive messages. 0 turns keep-alive off. */
    val keepAliveIntervalSeconds: Int = 0,
    /** Negotiate zlib compression with the server. */
    val compression: Boolean = false,
    /** Command written to the shell right after it opens. */
    val startupCommand: String? = null,
    /** Refuse a changed host key outright instead of asking, like OpenSSH's `StrictHostKeyChecking`. */
    val strictHostKeyChecking: Boolean = false,
    /** The section this connection is listed under, such as `Production`. Null when it isn't in one. */
    val group: String? = null,
    val tags: List<String> = emptyList(),
)
