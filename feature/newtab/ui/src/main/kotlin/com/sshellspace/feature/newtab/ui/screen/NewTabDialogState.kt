package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.feature.terminal.domain.models.Shell

data class NewTabDialogState(
    val defaultShell: Shell? = null,
    val otherShells: List<Shell> = emptyList(),
    /** Saved connections from the vault. */
    val connections: List<SshConnection> = emptyList(),
    val newTabShortcutLabel: String? = null,
    val isLoading: Boolean = true,
)
