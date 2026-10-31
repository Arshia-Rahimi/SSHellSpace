package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.TabParams
import kotlin.uuid.Uuid

/** Opens a terminal tab connected to a saved SSH connection. */
data class SshTerminalParams(
    val connectionId: Uuid,
    val title: String,
) : TabParams
