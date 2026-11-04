package com.sshellspace.feature.vault.sshkeys

import com.sshellspace.core.domain.models.Algorithm

sealed interface SshKeysPageAction {
    data class SelectKey(val key: SshKey) : SshKeysPageAction
    data class SetSearch(val value: String) : SshKeysPageAction
    data class OpenCreate(val method: CreationMethod) : SshKeysPageAction
    data class SwitchMethod(val method: CreationMethod) : SshKeysPageAction
    data object CloseSidebar : SshKeysPageAction
    data object DismissError : SshKeysPageAction

    sealed interface Editing : SshKeysPageAction {
        data class SetName(val value: String) : Editing
        data object Save : Editing
        data object RequestDelete : Editing
    }

    sealed interface VaultKey : SshKeysPageAction {
        data class SetName(val value: String) : VaultKey
        data class SetComment(val value: String) : VaultKey
        data class SetAlgorithm(val value: Algorithm) : VaultKey
        data object Save : VaultKey
    }

    sealed interface DeviceKey : SshKeysPageAction {
        data class SetName(val value: String) : DeviceKey
        data class SetComment(val value: String) : DeviceKey
        data class SetFileName(val value: String) : DeviceKey
        data class SetPath(val value: String) : DeviceKey
        data class SetAlgorithm(val value: Algorithm) : DeviceKey
        data class SetPassphrase(val value: String) : DeviceKey
        data class TogglePassphraseVisibility(val value: Boolean) : DeviceKey
        data object Save : DeviceKey
    }

    sealed interface ImportKey : SshKeysPageAction {
        data class SetName(val value: String) : ImportKey
        data class SetPath(val value: String) : ImportKey
        data class SetPassphrase(val value: String) : ImportKey
        data class TogglePassphraseVisibility(val value: Boolean) : ImportKey
        data object Save : ImportKey
    }

    sealed interface PasteKey : SshKeysPageAction {
        data class SetName(val value: String) : PasteKey
        data class SetPrivateKey(val value: String) : PasteKey
        data class SetPassphrase(val value: String) : PasteKey
        data class TogglePassphraseVisibility(val value: Boolean) : PasteKey
        data object Save : PasteKey
    }
}
