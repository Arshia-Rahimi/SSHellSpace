package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.feature.terminal.domain.models.Shell

data class NewTabDialogState(
    val defaultShell: Shell? = null,
    val otherShells: List<Shell> = emptyList(),
    val newTabShortcutLabel: String? = null,
    val isLoading: Boolean = true,
)
