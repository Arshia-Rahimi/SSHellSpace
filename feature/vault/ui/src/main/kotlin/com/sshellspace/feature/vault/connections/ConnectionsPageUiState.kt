package com.sshellspace.feature.vault.connections

import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.feature.vault.domain.SshConnectionDraft
import kotlin.uuid.Uuid

data class ConnectionsPageUiState(
    val connections: List<SshConnection> = emptyList(),
    val keys: List<SshKeyInfo> = emptyList(),
    val searchQuery: String = "",
    val sidebar: ConnectionSidebarState? = null,
    val isSaveInFlight: Boolean = false,
    val errorMessage: String? = null,
) {
    val filteredConnections: List<SshConnection>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return connections
            return connections.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.host.contains(query, ignoreCase = true) ||
                    it.username.contains(query, ignoreCase = true)
            }
        }

    /** Validation for the open form. Required fields only report once the user has tried to save. */
    val formErrors: ConnectionFormErrors
        get() = sidebar?.let { it.form.validate(connections, it.editingId, it.showRequiredErrors) }
            ?: ConnectionFormErrors()

    /** Connections that can be picked as the jump host of the open form without creating a loop. */
    val jumpHostCandidates: List<SshConnection>
        get() {
            val editingId = sidebar?.editingId ?: return connections
            val byId = connections.associateBy { it.id }
            return connections.filter { candidate ->
                generateSequence(candidate) { byId[it.options.jumpHostId ?: return@generateSequence null] }
                    .none { it.id == editingId }
            }
        }

    fun keyName(id: Uuid): String? = keys.firstOrNull { it.id == id }?.name
    fun connectionName(id: Uuid): String? = connections.firstOrNull { it.id == id }?.name
}

data class ConnectionSidebarState(
    /** Null while creating a new connection. */
    val editingId: Uuid?,
    val form: ConnectionForm,
    val showAdvanced: Boolean = false,
    val secretVisible: Boolean = false,
    val showRequiredErrors: Boolean = false,
) {
    val isNew: Boolean get() = editingId == null
}

enum class AuthMethod(val label: String, val description: String) {
    Password(
        label = "Password",
        description = "Sign in with the account password. Leave it empty to be asked every time you connect.",
    ),
    StoredKey(
        label = "SSH key from vault",
        description = "Use one of the keys from the SSH Keys page.",
    ),
    KeyFile(
        label = "Private key file",
        description = "Use a private key file on this device that isn't in the vault.",
    ),
    Agent(
        label = "SSH agent",
        description = "Use the keys loaded in your running SSH agent (SSH_AUTH_SOCK). Nothing is stored here.",
    ),
    KeyboardInteractive(
        label = "Keyboard-interactive",
        description = "For servers that ask questions such as a one-time code or 2FA. " +
            "A saved password answers password prompts; other prompts are asked when you connect.",
    ),
}

/** Editable copy of a connection. Numeric fields are kept as text so partial input isn't lost. */
data class ConnectionForm(
    val name: String = "",
    val host: String = "",
    val port: String = DEFAULT_SSH_PORT.toString(),
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.Password,
    val password: String = "",
    val keyId: Uuid? = null,
    val keyFilePath: String = "",
    val keyFilePassphrase: String = "",
    val jumpHostId: Uuid? = null,
    val connectTimeout: String = SshConnectionOptions().connectTimeoutSeconds.toString(),
    val keepAliveInterval: String = SshConnectionOptions().keepAliveIntervalSeconds.toString(),
    val compression: Boolean = false,
    val startupCommand: String = "",
    val strictHostKeyChecking: Boolean = false,
    /** The password or passphrase already saved, which can't be shown again. Newly typed text can. */
    val savedSecret: String = "",
) {
    fun toDraft() = SshConnectionDraft(
        name = name.trim(),
        host = host.trim(),
        port = port.trim().toInt(),
        username = username.trim(),
        auth = when (authMethod) {
            AuthMethod.Password -> SshConnectionAuth.Password(password.ifEmpty { null })
            AuthMethod.StoredKey -> SshConnectionAuth.StoredKey(requireNotNull(keyId))
            AuthMethod.KeyFile -> SshConnectionAuth.KeyFile(keyFilePath.trim(), keyFilePassphrase.ifEmpty { null })
            AuthMethod.Agent -> SshConnectionAuth.Agent
            AuthMethod.KeyboardInteractive -> SshConnectionAuth.KeyboardInteractive(password.ifEmpty { null })
        },
        options = SshConnectionOptions(
            jumpHostId = jumpHostId,
            connectTimeoutSeconds = connectTimeout.trim().toInt(),
            keepAliveIntervalSeconds = keepAliveInterval.trim().toInt(),
            compression = compression,
            startupCommand = startupCommand.trim().ifEmpty { null },
            strictHostKeyChecking = strictHostKeyChecking,
        ),
    )

    fun validate(
        connections: List<SshConnection>,
        editingId: Uuid?,
        showRequired: Boolean,
    ): ConnectionFormErrors {
        fun required(value: String, message: String) = if (showRequired && value.isBlank()) message else null

        val trimmedName = name.trim()
        return ConnectionFormErrors(
            name = required(name, "Name is required")
                ?: "$trimmedName already exists".takeIf {
                    trimmedName.isNotEmpty() &&
                        connections.any { c -> c.id != editingId && c.name.equals(trimmedName, ignoreCase = true) }
                },
            host = required(host, "Host is required")
                ?: "Host can't contain spaces".takeIf { host.trim().contains(' ') },
            port = numberError(port, 1..MAX_PORT, "Port must be between 1 and $MAX_PORT"),
            username = required(username, "Username is required"),
            auth = when (authMethod) {
                AuthMethod.StoredKey -> "Choose a key".takeIf { showRequired && keyId == null }
                AuthMethod.KeyFile -> required(keyFilePath, "Key file path is required")
                else -> null
            },
            connectTimeout = numberError(connectTimeout, 1..MAX_SECONDS, "Use 1 to $MAX_SECONDS seconds"),
            keepAliveInterval = numberError(keepAliveInterval, 0..MAX_SECONDS, "Use 0 to $MAX_SECONDS seconds"),
        )
    }

    private fun numberError(value: String, range: IntRange, message: String): String? =
        message.takeIf { value.trim().toIntOrNull()?.let { it in range } != true }

    private companion object {
        const val MAX_PORT = 65535
        const val MAX_SECONDS = 3600
    }
}

data class ConnectionFormErrors(
    val name: String? = null,
    val host: String? = null,
    val port: String? = null,
    val username: String? = null,
    val auth: String? = null,
    val connectTimeout: String? = null,
    val keepAliveInterval: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, host, port, username, auth, connectTimeout, keepAliveInterval).any { it != null }

    /** Advanced fields are hidden by default, so their errors need to open that section. */
    val hasAdvancedErrors: Boolean
        get() = connectTimeout != null || keepAliveInterval != null
}

fun SshConnection.toForm(): ConnectionForm {
    val base = ConnectionForm(
        name = name,
        host = host,
        port = port.toString(),
        username = username,
        jumpHostId = options.jumpHostId,
        connectTimeout = options.connectTimeoutSeconds.toString(),
        keepAliveInterval = options.keepAliveIntervalSeconds.toString(),
        compression = options.compression,
        startupCommand = options.startupCommand.orEmpty(),
        strictHostKeyChecking = options.strictHostKeyChecking,
    )
    return when (val auth = auth) {
        is SshConnectionAuth.Password -> base.copy(
            authMethod = AuthMethod.Password,
            password = auth.password.orEmpty(),
            savedSecret = auth.password.orEmpty(),
        )
        is SshConnectionAuth.StoredKey -> base.copy(authMethod = AuthMethod.StoredKey, keyId = auth.keyId)
        is SshConnectionAuth.KeyFile -> base.copy(
            authMethod = AuthMethod.KeyFile,
            keyFilePath = auth.path,
            keyFilePassphrase = auth.passphrase.orEmpty(),
            savedSecret = auth.passphrase.orEmpty(),
        )
        SshConnectionAuth.Agent -> base.copy(authMethod = AuthMethod.Agent)
        is SshConnectionAuth.KeyboardInteractive -> base.copy(
            authMethod = AuthMethod.KeyboardInteractive,
            password = auth.password.orEmpty(),
            savedSecret = auth.password.orEmpty(),
        )
    }
}

val SshConnectionAuth.method: AuthMethod
    get() = when (this) {
        is SshConnectionAuth.Password -> AuthMethod.Password
        is SshConnectionAuth.StoredKey -> AuthMethod.StoredKey
        is SshConnectionAuth.KeyFile -> AuthMethod.KeyFile
        SshConnectionAuth.Agent -> AuthMethod.Agent
        is SshConnectionAuth.KeyboardInteractive -> AuthMethod.KeyboardInteractive
    }

val SshConnection.address: String
    get() = buildString {
        append(username).append('@').append(host)
        if (port != DEFAULT_SSH_PORT) append(':').append(port)
    }
