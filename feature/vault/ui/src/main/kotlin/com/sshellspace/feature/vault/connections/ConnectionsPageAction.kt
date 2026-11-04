package com.sshellspace.feature.vault.connections

import com.sshellspace.core.domain.models.SshConnection

sealed interface ConnectionsPageAction {
    data class SetSearch(val value: String) : ConnectionsPageAction
    data object OpenCreate : ConnectionsPageAction
    data class Select(val connection: SshConnection) : ConnectionsPageAction
    data object CloseSidebar : ConnectionsPageAction

    /** Changes the open form. One action covers every field, since each one is a plain copy. */
    data class EditForm(val update: (ConnectionForm) -> ConnectionForm) : ConnectionsPageAction
    data object ToggleAdvanced : ConnectionsPageAction
    data object ToggleSecretVisible : ConnectionsPageAction

    /** Opens a terminal tab connected to the saved [connection]. */
    data class Connect(val connection: SshConnection) : ConnectionsPageAction

    data object Save : ConnectionsPageAction

    /** Saves the open form, then connects to it. */
    data object SaveAndConnect : ConnectionsPageAction
    data object Duplicate : ConnectionsPageAction
    data object RequestDelete : ConnectionsPageAction

    /** Shows only connections with [tag], or all of them when it's null. */
    data class SetTagFilter(val tag: String?) : ConnectionsPageAction
    data class ToggleGroup(val name: String) : ConnectionsPageAction

    /** Reads `~/.ssh/config` and shows its hosts to choose from. */
    data object OpenSshConfigImport : ConnectionsPageAction
    data class ToggleSshConfigHost(val alias: String) : ConnectionsPageAction
    data object ConfirmSshConfigImport : ConnectionsPageAction
    data object DismissNotice : ConnectionsPageAction
}
