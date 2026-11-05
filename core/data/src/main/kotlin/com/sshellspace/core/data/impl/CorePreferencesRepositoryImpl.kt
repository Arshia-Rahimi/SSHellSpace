package com.sshellspace.core.data.impl

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import com.sshellspace.core.domain.models.CorePreference
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.UserTheme
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    override fun getShortcuts() = preference
        .map { it.shortcuts }

    override suspend fun setShortcut(id: String, chord: KeyChord?) {
        val current = preference.value
        val shortcuts = if (chord == null) current.shortcuts - id else current.shortcuts + (id to chord)
        val updated = current.copy(shortcuts = shortcuts)
        datasource.set(key, JsonSerializer.encodeToString(CorePreference.serializer(), updated))
    }
}
