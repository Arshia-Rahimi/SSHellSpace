package com.sshellspace.feature.vault.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.repositories.AppUpdater
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.shortcuts.hasModifier
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.UpdateTerminalPreferences
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.terminalThemeChoices
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

/** Settings shared by every vault. Touches no vault data, so it also runs on the unlock screen. */
class GeneralSettingsViewModel(
    private val corePreferencesRepository: CorePreferencesRepository,
    private val terminalPreferencesRepository: TerminalPreferencesRepository,
    private val shellDiscoveryService: ShellDiscoveryService,
    private val shortcutHandler: ShortcutHandler,
    private val appUpdater: AppUpdater,
) : ViewModel() {
    private val discoveredShells = MutableStateFlow<List<Shell>?>(null)
    private val recording = MutableStateFlow<ShortcutRecording?>(null)

    private val themeImportError = MutableStateFlow<String?>(null)
    private val terminalThemeImportError = MutableStateFlow<String?>(null)
    private val confirmingRestart = MutableStateFlow<Int?>(null)

    private val appearance = combine(
        corePreferencesRepository.getTheme(),
        corePreferencesRepository.getColorScheme(),
        corePreferencesRepository.getUserThemes(),
        corePreferencesRepository.getActiveUserTheme(),
        themeImportError,
    ) { theme, colorSchemeId, userThemes, activeUserTheme, importError ->
        GeneralSettingsUiState(
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

    private val updates = combine(
        appUpdater.state,
        corePreferencesRepository.getAutoUpdate(),
        corePreferencesRepository.getUpdateCheckIntervalHours(),
        corePreferencesRepository.getLastUpdateCheck(),
        confirmingRestart,
    ) { status, automatic, intervalHours, lastChecked, confirming ->
        UpdateSettings(
            currentVersion = appUpdater.currentVersion,
            status = status,
            automatic = automatic,
            checkIntervalHours = intervalHours,
            lastChecked = lastChecked,
            confirmingRestart = confirming,
        )
    }

    private val appearanceAndUpdates = combine(appearance, terminalThemes, updates) { state, terminalThemes, updates ->
        state.copy(
            terminalThemes = terminalThemes.choices,
            importedTerminalThemes = terminalThemes.imported,
            terminalThemeImportError = terminalThemes.importError,
            updates = updates,
        )
    }

    val uiState = combine(
        appearanceAndUpdates,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), GeneralSettingsUiState())

    init {
        viewModelScope.launch {
            val shells = withContext(Dispatchers.IO) {
                runCatching { shellDiscoveryService.discover() }.getOrDefault(emptyList())
            }
            discoveredShells.update { shells }
        }
    }

    fun execute(action: GeneralSettingsAction) {
        when (action) {
            is GeneralSettingsAction.SetTheme -> viewModelScope.launch {
                corePreferencesRepository.setTheme(action.value)
            }
            is GeneralSettingsAction.SetColorScheme -> viewModelScope.launch {
                themeImportError.update { null }
                corePreferencesRepository.setColorScheme(action.value.id)
            }
            is GeneralSettingsAction.SetUserTheme -> viewModelScope.launch {
                themeImportError.update { null }
                corePreferencesRepository.setActiveUserTheme(action.name)
            }
            is GeneralSettingsAction.ImportTheme -> importTheme(action.path)
            is GeneralSettingsAction.RemoveUserTheme -> viewModelScope.launch {
                corePreferencesRepository.deleteUserTheme(action.name)
                if (terminalPreferencesRepository.get().value.terminalThemeName == action.name) {
                    terminalPreferencesRepository.setTerminalThemeName(null)
                }
            }
            is GeneralSettingsAction.SetTerminalTheme -> viewModelScope.launch {
                terminalThemeImportError.update { null }
                terminalPreferencesRepository.setTerminalThemeName(action.name)
            }
            is GeneralSettingsAction.ImportTerminalTheme -> importTerminalTheme(action.path)
            is GeneralSettingsAction.RemoveTerminalTheme -> viewModelScope.launch {
                corePreferencesRepository.deleteTerminalTheme(action.name)
                if (terminalPreferencesRepository.get().value.terminalThemeName == action.name) {
                    terminalPreferencesRepository.setTerminalThemeName(null)
                }
            }
            is GeneralSettingsAction.SetDefaultShell -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(defaultShell = action.value))
            }
            is GeneralSettingsAction.SetAutoReconnect -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(autoReconnect = action.enabled))
            }
            is GeneralSettingsAction.SetKeepSessionsWhenLocked -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(keepSessionsWhenLocked = action.enabled))
            }
            is GeneralSettingsAction.SetRestoreTabs -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(restoreTabs = action.enabled))
            }

            is GeneralSettingsAction.Update.SetAutomatic -> viewModelScope.launch {
                corePreferencesRepository.setAutoUpdate(action.enabled)
            }
            is GeneralSettingsAction.Update.SetCheckInterval -> viewModelScope.launch {
                corePreferencesRepository.setUpdateCheckIntervalHours(action.hours)
            }
            GeneralSettingsAction.Update.Check -> appUpdater.check()
            GeneralSettingsAction.Update.Download -> appUpdater.download()
            GeneralSettingsAction.Update.Restart -> {
                val open = appUpdater.openSessionCount()
                if (open > 0) confirmingRestart.update { open } else appUpdater.restartToInstall()
            }
            GeneralSettingsAction.Update.ConfirmRestart -> {
                confirmingRestart.update { null }
                appUpdater.restartToInstall()
            }
            GeneralSettingsAction.Update.CancelRestart -> confirmingRestart.update { null }

            is GeneralSettingsAction.Shortcut.StartRecording -> setRecording(ShortcutRecording(action.shortcut))
            is GeneralSettingsAction.Shortcut.Record -> record(action.chord)
            GeneralSettingsAction.Shortcut.CancelRecording -> setRecording(null)
            is GeneralSettingsAction.Shortcut.Reset -> viewModelScope.launch {
                shortcutHandler.resetChord(action.shortcut)
            }
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
