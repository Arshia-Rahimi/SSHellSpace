package com.sshellspace.feature.vault.sshkeys

import com.sshellspace.feature.vault.SshKey

sealed interface SshKeysPageAction {
    data class SelectKey(val key: SshKey) : SshKeysPageAction
    data object OpenNewKeyDialog: SshKeysPageAction
}
