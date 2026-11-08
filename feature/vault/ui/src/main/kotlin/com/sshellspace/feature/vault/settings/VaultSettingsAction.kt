package com.sshellspace.feature.vault.settings

sealed interface VaultSettingsAction {
    data object LockVault : VaultSettingsAction

    /** Checks [password], which is empty for a vault without one, and only then asks where to save with [pickDestination]. */
    data class ExportVault(val password: String, val pickDestination: suspend () -> String?) : VaultSettingsAction
    data class SetVaultEdit(val edit: VaultEdit?) : VaultSettingsAction
    data class ChangeVaultPassword(val current: String, val password: String, val confirmation: String) : VaultSettingsAction

    /** [password] is empty for a vault without one. */
    data class DeleteVault(val password: String) : VaultSettingsAction
    data object ClearVaultError : VaultSettingsAction
    data class SetAutoLockMinutes(val minutes: Int) : VaultSettingsAction
}
