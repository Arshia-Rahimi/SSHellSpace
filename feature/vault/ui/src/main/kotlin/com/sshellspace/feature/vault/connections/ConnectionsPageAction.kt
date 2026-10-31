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

    data object Save : ConnectionsPageAction
    data object Duplicate : ConnectionsPageAction
    data object RequestDelete : ConnectionsPageAction
}
