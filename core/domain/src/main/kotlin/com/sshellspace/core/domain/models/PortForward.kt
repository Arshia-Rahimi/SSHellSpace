package com.sshellspace.core.domain.models

import kotlin.uuid.Uuid

/** A saved port forward that runs over the saved SSH connection [connectionId]. */
data class PortForward(
    val id: Uuid,
    val name: String,
    val connectionId: Uuid,
    val type: PortForwardType,
    /** Host the listening socket binds to: on this device for local and dynamic forwards, on the server for remote. */
    val bindHost: String = LOCALHOST,
    val bindPort: Int,
    /** Where forwarded connections go. Unused for dynamic forwards. */
    val destinationHost: String = LOCALHOST,
    val destinationPort: Int = 0,
)

enum class PortForwardType {
    /** `ssh -L`: a port on this device reaches a host the server can see. */
    Local,

    /** `ssh -R`: a port on the server reaches a host this device can see. */
    Remote,

    /** `ssh -D`: a SOCKS proxy on this device that routes through the server. */
    Dynamic,
}

const val LOCALHOST = "127.0.0.1"
