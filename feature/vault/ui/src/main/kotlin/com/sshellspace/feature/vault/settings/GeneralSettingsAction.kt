package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface GeneralSettingsAction {
    data class SetTheme(val value: ThemePreference) : GeneralSettingsAction
    data class SetColorScheme(val value: AppColorScheme) : GeneralSettingsAction
    data class SetUserTheme(val name: String) : GeneralSettingsAction
    data class ImportTheme(val path: String) : GeneralSettingsAction
    data class RemoveUserTheme(val name: String) : GeneralSettingsAction

    /** A null [name] makes the terminal follow the app theme. */
    data class SetTerminalTheme(val name: String?) : GeneralSettingsAction

    /** Imports terminal colors from the file at [path] and switches the terminal to them. */
    data class ImportTerminalTheme(val path: String) : GeneralSettingsAction
    data class RemoveTerminalTheme(val name: String) : GeneralSettingsAction
    data class SetDefaultShell(val value: Shell) : GeneralSettingsAction
    data class SetAutoReconnect(val enabled: Boolean) : GeneralSettingsAction
    data class SetRestoreTabs(val enabled: Boolean) : GeneralSettingsAction
    data class SetKeepSessionsWhenLocked(val enabled: Boolean) : GeneralSettingsAction

    sealed interface Update : GeneralSettingsAction {
        data class SetAutomatic(val enabled: Boolean) : Update
        data class SetCheckInterval(val hours: Int) : Update
        data object Check : Update
        data object Download : Update

        /** Installs the downloaded update, first asking if that would close anything. */
        data object Restart : Update
        data object ConfirmRestart : Update
        data object CancelRestart : Update
    }

    sealed interface Shortcut : GeneralSettingsAction {
        data class StartRecording(val shortcut: AppShortcut) : Shortcut
        data class Record(val chord: KeyChord) : Shortcut
        data object CancelRecording : Shortcut
        data class Reset(val shortcut: AppShortcut) : Shortcut
    }
}
