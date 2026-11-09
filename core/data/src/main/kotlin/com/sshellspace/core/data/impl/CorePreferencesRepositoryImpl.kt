package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.SavedWindow
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.UserTheme
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

internal class CorePreferencesRepositoryImpl(
    private val datasource: PreferencesDatasource,
) : CorePreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val key = CorePreference::class.qualifiedName!!

    private val preference: StateFlow<CorePreference> = datasource.observeAll()
        .map { rows -> rows.firstOrNull { it.key == key }?.value }
        .map { stored -> stored?.let { JsonSerializer.decodeFromString(CorePreference.serializer(), it) } }
        .mapNotNull { it }
        .stateIn(scope, SharingStarted.Eagerly, CorePreference())

    // A row of its own: it's written in the background, and every write to the row above starts from a copy that can lag
    // behind, so sharing that row could undo a setting the user had just changed.
    private val lastUpdateCheckKey = "$key.lastUpdateCheck"
    private val lastUpdateCheck: StateFlow<Long?> = datasource.observeAll()
        .map { rows -> rows.firstOrNull { it.key == lastUpdateCheckKey }?.value?.toLongOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // A row of its own for the same reason: it's written at startup, possibly before the row above has loaded.
    private val welcomeSeenKey = "$key.welcomeSeen"

    // A row of its own: it's written whenever the window is resized, and read before the row above has loaded.
    private val windowKey = "$key.window"

    override fun getTheme() = preference
        .map { it.themePreference }

    override suspend fun setTheme(theme: ThemePreference) {
        val current = preference.value
        val updated = current.copy(themePreference = theme)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override fun getColorScheme() = preference
        .map { it.colorSchemeId }

    override suspend fun setColorScheme(id: String) {
        val current = preference.value
        val updated = current.copy(colorSchemeId = id, activeUserThemeName = null)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override fun getUserThemes() = preference
        .map { it.userThemes }

    override fun getActiveUserTheme() = preference
        .map { current -> current.userThemes.firstOrNull { it.name == current.activeUserThemeName } }

    override suspend fun importUserTheme(json: String): UserTheme {
        val theme = parseUserTheme(json)
        val current = preference.value
        val userThemes = if (current.userThemes.any { it.name == theme.name }) {
            current.userThemes.map { if (it.name == theme.name) theme else it }
        } else {
            current.userThemes + theme
        }
        // Saved and activated in one write: a second write would start from a preference that hasn't caught up yet.
        val updated = current.copy(userThemes = userThemes, activeUserThemeName = theme.name)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
        return theme
    }

    override suspend fun setActiveUserTheme(name: String) {
        val current = preference.value
        val updated = current.copy(activeUserThemeName = name)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override suspend fun deleteUserTheme(name: String) {
        val current = preference.value
        val updated = current.copy(
            userThemes = current.userThemes.filterNot { it.name == name },
            activeUserThemeName = current.activeUserThemeName.takeUnless { it == name },
        )
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override fun getTerminalThemes() = preference
        .map { it.terminalThemes }

    override suspend fun importTerminalTheme(json: String, fallbackName: String): TerminalColors {
        val theme = parseTerminalTheme(json, fallbackName)
        val current = preference.value
        val updated = current.copy(terminalThemes = current.terminalThemes.filterNot { it.name == theme.name } + theme)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
        return theme
    }

    override suspend fun deleteTerminalTheme(name: String) {
        val current = preference.value
        val updated = current.copy(terminalThemes = current.terminalThemes.filterNot { it.name == name })
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override fun getAutoLockMinutes() = preference
        .map { it.autoLockMinutes }

    override fun getAutoUpdate() = preference
        .map { it.autoUpdate }

    override suspend fun setAutoUpdate(enabled: Boolean) {
        val updated = preference.value.copy(autoUpdate = enabled)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override fun getUpdateCheckIntervalHours() = preference
        .map { it.updateCheckIntervalHours }

    override suspend fun setUpdateCheckIntervalHours(hours: Int) {
        val updated = preference.value.copy(updateCheckIntervalHours = hours)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }

    override fun getLastUpdateCheck(): Flow<Long?> = lastUpdateCheck

    override suspend fun setLastUpdateCheck(epochMillis: Long) {
        datasource.set(lastUpdateCheckKey, epochMillis.toString())
    }

    // Not a StateFlow: its initial value would show the welcome again until the row had loaded.
    override fun getWelcomeSeen(): Flow<Boolean> = datasource.observeAll()
        .map { rows -> rows.any { it.key == welcomeSeenKey } }

    override suspend fun setWelcomeSeen() {
        datasource.set(welcomeSeenKey, true.toString())
    }

    // Not a StateFlow, for the same reason as the welcome: the window is sized from the first value.
    override fun getWindow(): Flow<SavedWindow?> = datasource.observeAll()
        .map { rows ->
            rows.firstOrNull { it.key == windowKey }?.value?.let {
                runCatching { JsonSerializer.decodeFromString(SavedWindow.serializer(), it) }.getOrNull()
            }
        }

    override suspend fun setWindow(window: SavedWindow) {
        datasource.set(windowKey, JsonSerializer.encodeToString(SavedWindow.serializer(), window))
    }

    override fun getShortcuts() = preference
        .map { it.shortcuts }

    override suspend fun setShortcut(id: String, chord: KeyChord?) {
        val current = preference.value
        val shortcuts = if (chord == null) current.shortcuts - id else current.shortcuts + (id to chord)
        val updated = current.copy(shortcuts = shortcuts)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }
}
