package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface NewTabDialogAction {
    data class CreateNewTab(val shell: Shell) : NewTabDialogAction
}
