package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface SettingsPageAction {
    data class SetTheme(val value: ThemePreference) : SettingsPageAction
    data class SetColorScheme(val value: AppColorScheme) : SettingsPageAction
    data class SetDefaultShell(val value: Shell) : SettingsPageAction

    sealed interface Shortcut : SettingsPageAction {
        data class StartRecording(val shortcut: AppShortcut) : Shortcut
        data class Record(val chord: KeyChord) : Shortcut
        data object CancelRecording : Shortcut
        data class Reset(val shortcut: AppShortcut) : Shortcut
    }
}
