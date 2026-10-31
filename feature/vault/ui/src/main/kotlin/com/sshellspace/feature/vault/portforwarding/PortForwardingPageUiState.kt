package com.sshellspace.feature.vault.portforwarding

import com.sshellspace.core.domain.models.LOCALHOST
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.vault.domain.PortForwardDraft
import kotlin.uuid.Uuid

data class PortForwardingPageUiState(
    val forwards: List<PortForward> = emptyList(),
    val connections: List<SshConnection> = emptyList(),
    val states: Map<Uuid, PortForwardState> = emptyMap(),
    val sidebar: PortForwardSidebarState? = null,
    val isSaveInFlight: Boolean = false,
    val errorMessage: String? = null,
    /** A login question from a forward that is starting. */
    val login: LoginPrompt? = null,
) {
    val formErrors: PortForwardFormErrors
        get() = sidebar?.let { it.form.validate(forwards, it.editingId, it.showRequiredErrors) } ?: PortForwardFormErrors()

    fun connectionName(id: Uuid): String? = connections.firstOrNull { it.id == id }?.name
}

data class PortForwardSidebarState(
    /** Null while creating a new forward. */
    val editingId: Uuid?,
    val form: PortForwardForm,
    val showRequiredErrors: Boolean = false,
) {
    val isNew: Boolean get() = editingId == null
}

sealed interface LoginPrompt {
    val forwardId: Uuid

    data class HostKey(override val forwardId: Uuid, val prompt: HostKeyPrompt) : LoginPrompt
    data class Secret(override val forwardId: Uuid, val prompt: SecretPrompt) : LoginPrompt
    data class Challenge(override val forwardId: Uuid, val challenge: AuthChallenge) : LoginPrompt
}

val PortForwardType.label: String
    get() = when (this) {
        PortForwardType.Local -> "Local"
        PortForwardType.Remote -> "Remote"
        PortForwardType.Dynamic -> "SOCKS proxy"
    }

val PortForwardType.description: String
    get() = when (this) {
        PortForwardType.Local -> "Open a port on this device that reaches a host the server can see. Like ssh -L."
        PortForwardType.Remote -> "Open a port on the server that reaches a host this device can see. Like ssh -R."
        PortForwardType.Dynamic ->
            "Run a SOCKS proxy on this device that sends traffic through the server. Like ssh -D."
    }

/** Label for the listening port, which is on this device except for remote forwards. */
val PortForwardType.bindLabel: String
    get() = when (this) {
        PortForwardType.Local -> "Local port"
        PortForwardType.Remote -> "Server port"
        PortForwardType.Dynamic -> "SOCKS port"
    }

/** Where the destination host is resolved from, which is the other end from the listener. */
val PortForwardType.destinationHint: String
    get() = if (this == PortForwardType.Remote) "Resolved from this device" else "Resolved from the server"

/** `127.0.0.1:8080 → localhost:5432`, or just the listener for a SOCKS proxy. */
val PortForward.route: String
    get() = buildString {
        append(bindHost).append(':').append(bindPort)
        if (type != PortForwardType.Dynamic) append(" → ").append(destinationHost).append(':').append(destinationPort)
    }

/** Editable copy of a forward. Ports are kept as text so partial input isn't lost. */
data class PortForwardForm(
    val name: String = "",
    val connectionId: Uuid? = null,
    val type: PortForwardType = PortForwardType.Local,
    val bindHost: String = LOCALHOST,
    val bindPort: String = "",
    val destinationHost: String = "localhost",
    val destinationPort: String = "",
) {
    /** Used when the name is left empty, e.g. `8080 → localhost:5432`. */
    val suggestedName: String
        get() = when (type) {
            PortForwardType.Dynamic -> "SOCKS ${bindPort.trim()}"
            else -> "${bindPort.trim()} → ${destinationHost.trim()}:${destinationPort.trim()}"
        }

    fun toDraft(name: String) = PortForwardDraft(
        name = name,
        connectionId = requireNotNull(connectionId),
        type = type,
        bindHost = bindHost.trim(),
        bindPort = bindPort.trim().toInt(),
        destinationHost = destinationHost.trim(),
        destinationPort = destinationPort.trim().toIntOrNull() ?: 0,
    )

    fun validate(forwards: List<PortForward>, editingId: Uuid?, showRequired: Boolean): PortForwardFormErrors {
        fun required(value: String, message: String) = if (showRequired && value.isBlank()) message else null
        fun port(value: String) =
            required(value, "Port is required") ?: "Use a port from 1 to $MAX_PORT".takeIf {
                value.isNotBlank() && value.trim().toIntOrNull()?.let { it in 1..MAX_PORT } != true
            }

        val trimmedName = name.trim()
        val needsDestination = type != PortForwardType.Dynamic
        return PortForwardFormErrors(
            name = "$trimmedName already exists".takeIf {
                trimmedName.isNotEmpty() &&
                    forwards.any { f -> f.id != editingId && f.name.equals(trimmedName, ignoreCase = true) }
            },
            connection = "Choose a connection".takeIf { showRequired && connectionId == null },
            bind = port(bindPort) ?: "Address can't contain spaces".takeIf { bindHost.trim().contains(' ') },
            destination = if (needsDestination) {
                required(destinationHost, "Destination host is required")
                    ?: "Host can't contain spaces".takeIf { destinationHost.trim().contains(' ') }
                    ?: port(destinationPort)
            } else {
                null
            },
        )
    }

    private companion object {
        const val MAX_PORT = 65535
    }
}

data class PortForwardFormErrors(
    val name: String? = null,
    val connection: String? = null,
    val bind: String? = null,
    val destination: String? = null,
) {
    val hasErrors: Boolean get() = listOf(name, connection, bind, destination).any { it != null }
}

fun PortForward.toForm() = PortForwardForm(
    name = name,
    connectionId = connectionId,
    type = type,
    bindHost = bindHost,
    bindPort = bindPort.toString(),
    destinationHost = if (type == PortForwardType.Dynamic) "localhost" else destinationHost,
    destinationPort = if (type == PortForwardType.Dynamic) "" else destinationPort.toString(),
)
