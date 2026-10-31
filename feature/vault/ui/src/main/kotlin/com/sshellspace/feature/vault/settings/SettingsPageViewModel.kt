package com.sshellspace.feature.vault.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.shortcuts.hasModifier
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.UpdateTerminalPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsPageViewModel(
    private val corePreferencesRepository: CorePreferencesRepository,
    private val terminalPreferencesRepository: TerminalPreferencesRepository,
    private val shellDiscoveryService: ShellDiscoveryService,
    private val shortcutHandler: ShortcutHandler,
) : ViewModel() {
    private val discoveredShells = MutableStateFlow<List<Shell>?>(null)
    private val recording = MutableStateFlow<ShortcutRecording?>(null)

    private val appearance = combine(
        corePreferencesRepository.getTheme(),
        corePreferencesRepository.getColorScheme(),
    ) { theme, colorSchemeId -> theme to AppColorScheme.fromId(colorSchemeId) }

    val uiState = combine(
        appearance,
        terminalPreferencesRepository.get(),
        discoveredShells,
        shortcutHandler.bindings,
        recording,
    ) { (theme, colorScheme), terminalPrefs, shells, bindings, recording ->
        SettingsPageUiState(
            themePreference = theme,
            colorScheme = colorScheme,
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
                corePreferencesRepository.setColorScheme(action.value.id)
            }
            is SettingsPageAction.SetDefaultShell -> viewModelScope.launch {
                terminalPreferencesRepository.set(UpdateTerminalPreferences(defaultShell = action.value))
            }

            is SettingsPageAction.Shortcut.StartRecording -> setRecording(ShortcutRecording(action.shortcut))
            is SettingsPageAction.Shortcut.Record -> record(action.chord)
            SettingsPageAction.Shortcut.CancelRecording -> setRecording(null)
            is SettingsPageAction.Shortcut.Reset -> viewModelScope.launch {
                shortcutHandler.resetChord(action.shortcut)
            }
        }
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
