package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.TabParams
import kotlin.uuid.Uuid

/** Opens a file browser tab connected to a saved SSH connection. */
data class SftpParams(
    val connectionId: Uuid,
    val title: String,
) : TabParams
