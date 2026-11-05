package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.TabParams
import kotlin.uuid.Uuid

/** Opens a file browser tab connected to a saved SSH connection, or with no [connectionId], to this computer. */
data class SftpParams(
    val connectionId: Uuid?,
    val title: String,
) : TabParams {
    companion object {
        val ThisDevice = SftpParams(connectionId = null, title = "This device")
    }
}
