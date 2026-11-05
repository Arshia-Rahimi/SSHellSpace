package com.sshellspace.feature.vault.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesRepository
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.shortcuts.hasModifier
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.UpdateTerminalPreferences
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.terminalThemeChoices
import com.sshellspace.feature.vault.gate.credentialFrom
import com.sshellspace.feature.vault.gate.passwordMismatch
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_THEME_FILE_BYTES = 1024 * 1024L

private data class TerminalThemeState(
    val choices: List<TerminalColors>,
    val imported: Set<String>,
    val importError: String?,
)

class SettingsPageViewModel(
    private val corePreferencesRepository: CorePreferencesRepository,
    private val terminalPreferencesRepository: TerminalPreferencesRepository,
    private val shellDiscoveryService: ShellDiscoveryService,
    private val shortcutHandler: ShortcutHandler,
    private val vaultManager: VaultManager,
    private val vaultPreferencesRepository: VaultPreferencesRepository,
) : ViewModel() {
    private val discoveredShells = MutableStateFlow<List<Shell>?>(null)
    private val recording = MutableStateFlow<ShortcutRecording?>(null)

    private val themeImportError = MutableStateFlow<String?>(null)
    private val terminalThemeImportError = MutableStateFlow<String?>(null)
    private val vaultForm = MutableStateFlow(VaultForm())

    private val appearance = combine(
        corePreferencesRepository.getTheme(),
        corePreferencesRepository.getColorScheme(),
        corePreferencesRepository.getUserThemes(),
        corePreferencesRepository.getActiveUserTheme(),
        themeImportError,
    ) { theme, colorSchemeId, userThemes, activeUserTheme, importError ->
        SettingsPageUiState(
            themePreference = theme,
            colorScheme = AppColorScheme.fromId(colorSchemeId),
            userThemes = userThemes,
            activeUserThemeName = activeUserTheme?.name,
            themeImportError = importError,
        )
    }

    private val terminalThemes = combine(
        corePreferencesRepository.getTerminalThemes(),
        corePreferencesRepository.getUserThemes(),
        terminalThemeImportError,
    ) { imported, userThemes, importError ->
        TerminalThemeState(
            choices = terminalThemeChoices(imported, userThemes),
            imported = imported.mapTo(mutableSetOf()) { it.name },
            importError = importError,
        )
    }

    private val appearanceAndVault = combine(
        appearance,
        vaultManager.activeVault,
        vaultPreferencesRepository.getAutoLockMinutes(),
        vaultForm,
        terminalThemes,
    ) { appearance, vault, autoLockMinutes, form, terminalThemes ->
        appearance.copy(
            vaultName = vault?.name,
            vaultHasPassword = vault?.hasPassword ?: true,
            autoLockMinutes = autoLockMinutes,
            vaultForm = form,
            terminalThemes = terminalThemes.choices,
            importedTerminalThemes = terminalThemes.imported,
            terminalThemeImportError = terminalThemes.importError,
        )
    }

    val uiState = combine(
        appearanceAndVault,
        terminalPreferencesRepository.get(),
        discoveredShells,
        shortcutHandler.bindings,
        recording,
    ) { appearance, terminalPrefs, shells, bindings, recording ->
        appearance.copy(
            terminalThemeName = terminalPrefs.terminalThemeName,
            autoReconnect = terminalPrefs.autoReconnect,
            restoreTabs = terminalPrefs.restoreTabs,
            keepSessionsWhenLocked = terminalPrefs.keepSessionsWhenLocked,
            shells = listOf(SYSTEM_DEFAULT_SHELL) + shells.orEmpty(),
            defaultShell = terminalPrefs.defaultShell,
            isDiscoveringShells = shells == null,
            shortcuts = bindings.map { (shortcut, chord) -> ShortcutBinding(shortcut, chord) },
            recording = recording,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SettingsPageUiState())

    init {
        viewModelScope.launch {
            val shells = withContext(Dispatchers.IO) {
                runCatching { shellDiscoveryService.discover() }.getOrDefault(emptyList())
            }
            discoveredShells.update { shells }
        }
    }

    fun execute(action: SettingsPageAction) {
        when (action) {
            is SettingsPageAction.SetTheme -> viewModelScope.launch {
                corePreferencesRepository.setTheme(action.value)
            }
            is SettingsPageAction.SetColorScheme -> viewModelScope.launch {
                themeImportError.update { null }
                corePreferencesRepository.setColorScheme(action.value.id)
            }
            is SettingsPageAction.SetUserTheme -> viewModelScope.launch {
                themeImportError.update { null }
                corePreferencesRepository.setActiveUserTheme(action.name)
            }
            is SettingsPageAction.ImportTheme -> importTheme(action.path)
            is SettingsPageAction.RemoveUserTheme -> viewModelScope.launch {
                corePreferencesRepository.deleteUserTheme(action.name)
                if (terminalPreferencesRepository.get().value.terminalThemeName == action.name) {
                    terminalPreferencesRepository.setTerminalThemeName(null)
                }
            }
            is SettingsPageAction.SetTerminalTheme -> viewModelScope.launch {
                terminalThemeImportError.update { null }
                terminalPreferencesRepository.setTerminalThemeName(action.name)
            }
            is SettingsPageAction.ImportTerminalTheme -> importTerminalTheme(action.path)
            is SettingsPageAction.RemoveTerminalTheme -> viewModelScope.launch {
                corePreferencesRepository.deleteTerminalTheme(action.name)
                if (terminalPreferencesRepository.get().value.terminalThemeName == action.name) {
                    terminalPreferencesRepository.setTerminalThemeName(null)
                }
            }
            is SettingsPageAction.SetDefaultShell -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(defaultShell = action.value))
            }

            SettingsPageAction.LockVault -> viewModelScope.launch { vaultManager.lock() }
            is SettingsPageAction.SetVaultEdit -> vaultForm.update { VaultForm(edit = action.edit) }
            SettingsPageAction.ClearVaultError -> vaultForm.update { it.copy(error = null) }
            is SettingsPageAction.ExportVault -> exportVault(action)
            is SettingsPageAction.ChangeVaultPassword -> changeVaultPassword(action)
            is SettingsPageAction.DeleteVault -> runVaultAction { vault ->
                vaultManager.delete(vault.id, credentialFrom(action.password))
            }
            is SettingsPageAction.SetAutoReconnect -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(autoReconnect = action.enabled))
            }
            is SettingsPageAction.SetKeepSessionsWhenLocked -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(keepSessionsWhenLocked = action.enabled))
            }
            is SettingsPageAction.SetRestoreTabs -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(restoreTabs = action.enabled))
            }
            is SettingsPageAction.SetAutoLockMinutes -> viewModelScope.launch {
                vaultPreferencesRepository.setAutoLockMinutes(action.minutes)
            }

            is SettingsPageAction.Shortcut.StartRecording -> setRecording(ShortcutRecording(action.shortcut))
            is SettingsPageAction.Shortcut.Record -> record(action.chord)
            SettingsPageAction.Shortcut.CancelRecording -> setRecording(null)
            is SettingsPageAction.Shortcut.Reset -> viewModelScope.launch {
                shortcutHandler.resetChord(action.shortcut)
            }
        }
    }

    private fun exportVault(action: SettingsPageAction.ExportVault) {
        val credential = credentialFrom(action.password)
        runVaultAction { vault ->
            // First, so a mistyped password doesn't send the user through the save dialog for nothing.
            vaultManager.verifyCredential(vault.id, credential)
            val path = action.pickDestination() ?: return@runVaultAction
            vaultManager.export(vault.id, credential, path)
            vaultForm.update { VaultForm(notice = exportNotice(vault)) }
        }
    }

    private fun exportNotice(vault: VaultInfo) = if (vault.hasPassword) {
        "Exported ${vault.name}. You'll need its password to import it."
    } else {
        "Exported ${vault.name}. It has no password, so anyone with that file can read it."
    }

    private fun changeVaultPassword(action: SettingsPageAction.ChangeVaultPassword) {
        passwordMismatch(action.password, action.confirmation)?.let { problem ->
            vaultForm.update { it.copy(error = problem) }
            return
        }
        runVaultAction { vault ->
            vaultManager.changeCredential(vault.id, credentialFrom(action.current), credentialFrom(action.password))
            val message = if (action.password.isEmpty()) {
                "Password removed. Exports you made earlier still open with the old one."
            } else {
                "Password changed. Exports you made earlier still open with the old one."
            }
            vaultForm.update { VaultForm(notice = message) }
        }
    }

    /** Runs an action on the unlocked vault, one at a time, showing progress and any failure. */
    private fun runVaultAction(block: suspend (VaultInfo) -> Unit) {
        val vault = vaultManager.activeVault.value ?: return
        if (vaultForm.value.isBusy) return
        vaultForm.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch {
            val error = try {
                block(vault)
                null
            } catch (e: VaultException) {
                e.message
            } catch (e: IOException) {
                "Couldn't access the file: ${e.message}"
            }
            vaultForm.update { it.copy(isBusy = false, error = error) }
        }
    }

    private fun importTheme(path: String) {
        viewModelScope.launch {
            val error = try {
                corePreferencesRepository.importUserTheme(readThemeFile(path))
                null
            } catch (e: IOException) {
                "Couldn't read the file: ${e.message}"
            } catch (e: IllegalArgumentException) {
                "Couldn't import the theme: ${e.message}"
            }
            themeImportError.update { error }
        }
    }

    private fun importTerminalTheme(path: String) {
        viewModelScope.launch {
            val error = try {
                val theme = corePreferencesRepository.importTerminalTheme(readThemeFile(path), File(path).nameWithoutExtension)
                terminalPreferencesRepository.setTerminalThemeName(theme.name)
                null
            } catch (e: IOException) {
                "Couldn't read the file: ${e.message}"
            } catch (e: IllegalArgumentException) {
                "Couldn't import the theme: ${e.message}"
            }
            terminalThemeImportError.update { error }
        }
    }

    private suspend fun readThemeFile(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.length() > MAX_THEME_FILE_BYTES) throw IOException("it's too large to be a theme")
        file.readText()
    }

    private fun record(chord: KeyChord) {
        val current = recording.value ?: return
        if (!chord.hasModifier) {
            recording.update { current.copy(error = "Include Cmd, Ctrl or Alt in the shortcut.") }
            return
        }
        val conflict = shortcutHandler.bindings.value.entries
            .firstOrNull { (shortcut, bound) -> shortcut != current.shortcut && bound == chord }
            ?.key
        if (conflict != null) {
            recording.update { current.copy(error = "Already used by \"${conflict.label}\".") }
            return
        }
        setRecording(null)
        viewModelScope.launch {
            shortcutHandler.setChord(current.shortcut, chord)
        }
    }

    private fun setRecording(value: ShortcutRecording?) {
        recording.update { value }
        shortcutHandler.isPaused = value != null
    }

    override fun onCleared() {
        shortcutHandler.isPaused = false
        super.onCleared()
    }
}
