package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.models.DEFAULT_AUTO_LOCK_MINUTES
import com.sshellspace.core.domain.models.DEFAULT_UPDATE_CHECK_INTERVAL_HOURS
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.UserTheme
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.feature.terminal.domain.models.Shell

data class SettingsPageUiState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    val colorScheme: AppColorScheme = AppColorScheme.Default,
    val userThemes: List<UserTheme> = emptyList(),
    val activeUserThemeName: String? = null,
    val terminalThemeName: String? = null,
    /** Every theme the terminal can use, including [importedTerminalThemes]. */
    val terminalThemes: List<TerminalColors> = emptyList(),
    /** The names of themes imported for the terminal alone, which can be removed. */
    val importedTerminalThemes: Set<String> = emptySet(),
    val terminalThemeImportError: String? = null,
    val themeImportError: String? = null,
    val vaultName: String? = null,
    val vaultHasPassword: Boolean = true,
    val vaultForm: VaultForm = VaultForm(),
    val autoLockMinutes: Int = DEFAULT_AUTO_LOCK_MINUTES,
    val autoReconnect: Boolean = true,
    val restoreTabs: Boolean = true,
    val keepSessionsWhenLocked: Boolean = true,
    val shells: List<Shell> = listOf(SYSTEM_DEFAULT_SHELL),
    val defaultShell: Shell = SYSTEM_DEFAULT_SHELL,
    val isDiscoveringShells: Boolean = true,
    val shortcuts: List<ShortcutBinding> = emptyList(),
    val recording: ShortcutRecording? = null,
    val updates: UpdateSettings = UpdateSettings(),
)

data class UpdateSettings(
    val currentVersion: String = "",
    val status: AppUpdateState = AppUpdateState.Idle,
    val automatic: Boolean = true,
    val checkIntervalHours: Int = DEFAULT_UPDATE_CHECK_INTERVAL_HOURS,
    /** Epoch milliseconds. */
    val lastChecked: Long? = null,
    /** How many tabs and port forwards a restart would close, while asking whether to go ahead. */
    val confirmingRestart: Int? = null,
)

/** A vault action that needs more than one click, open in the Vault section. */
enum class VaultEdit { ChangePassword, Export, Delete }

data class VaultForm(
    val edit: VaultEdit? = null,
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Confirms something that finished, such as an export. */
    val notice: String? = null,
)

data class ShortcutBinding(
    val shortcut: AppShortcut,
    val chord: KeyChord,
) {
    val isDefault: Boolean get() = chord == shortcut.defaultChord
}

data class ShortcutRecording(
    val shortcut: AppShortcut,
    val error: String? = null,
)

val SYSTEM_DEFAULT_SHELL = Shell(name = "", isDeviceDefault = true)

val ThemePreference.label: String
    get() = when (this) {
        ThemePreference.SYSTEM_DEFAULT -> "System"
        ThemePreference.LIGHT -> "Light"
        ThemePreference.DARK -> "Dark"
    }

val Shell.label: String
    get() = when {
        command == null -> "System default"
        isDeviceDefault -> "$name (system default) — $command"
        else -> "$name — $command"
    }
