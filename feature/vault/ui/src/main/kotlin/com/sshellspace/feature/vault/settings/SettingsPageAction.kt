package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface SettingsPageAction {
    data class SetTheme(val value: ThemePreference) : SettingsPageAction
    data class SetColorScheme(val value: AppColorScheme) : SettingsPageAction
    data class SetUserTheme(val name: String) : SettingsPageAction
    data class ImportTheme(val path: String) : SettingsPageAction
    data class RemoveUserTheme(val name: String) : SettingsPageAction

    /** A null [name] makes the terminal follow the app theme. */
    data class SetTerminalTheme(val name: String?) : SettingsPageAction

    /** Imports terminal colors from the file at [path] and switches the terminal to them. */
    data class ImportTerminalTheme(val path: String) : SettingsPageAction
    data class RemoveTerminalTheme(val name: String) : SettingsPageAction
    data class SetDefaultShell(val value: Shell) : SettingsPageAction
    data object LockVault : SettingsPageAction

    /** Checks [password], which is empty for a vault without one, and only then asks where to save with [pickDestination]. */
    data class ExportVault(val password: String, val pickDestination: suspend () -> String?) : SettingsPageAction
    data class SetVaultEdit(val edit: VaultEdit?) : SettingsPageAction
    data class ChangeVaultPassword(val current: String, val password: String, val confirmation: String) : SettingsPageAction

    /** [password] is empty for a vault without one. */
    data class DeleteVault(val password: String) : SettingsPageAction
    data object ClearVaultError : SettingsPageAction
    data class SetAutoReconnect(val enabled: Boolean) : SettingsPageAction
    data class SetRestoreTabs(val enabled: Boolean) : SettingsPageAction
    data class SetKeepSessionsWhenLocked(val enabled: Boolean) : SettingsPageAction
    data class SetAutoLockMinutes(val minutes: Int) : SettingsPageAction

    sealed interface Update : SettingsPageAction {
        data class SetAutomatic(val enabled: Boolean) : Update
        data class SetCheckInterval(val hours: Int) : Update
        data object Check : Update
        data object Download : Update

        /** Installs the downloaded update, first asking if that would close anything. */
        data object Restart : Update
        data object ConfirmRestart : Update
        data object CancelRestart : Update
    }

    sealed interface Shortcut : SettingsPageAction {
        data class StartRecording(val shortcut: AppShortcut) : Shortcut
        data class Record(val chord: KeyChord) : Shortcut
        data object CancelRecording : Shortcut
        data class Reset(val shortcut: AppShortcut) : Shortcut
    }
}
