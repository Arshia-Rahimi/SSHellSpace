package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface NewTabDialogAction {
    data class CreateNewTab(val shell: Shell) : NewTabDialogAction

    /** Opens an SSH tab connected to the saved [connection]. */
    data class Connect(val connection: SshConnection) : NewTabDialogAction
}
