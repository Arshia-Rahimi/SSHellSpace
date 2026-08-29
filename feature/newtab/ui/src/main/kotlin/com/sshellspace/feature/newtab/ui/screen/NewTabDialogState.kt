package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.feature.terminal.domain.models.Shell

data class NewTabDialogState(
    val discoveredShells: List<Shell> = emptyList(),
)
