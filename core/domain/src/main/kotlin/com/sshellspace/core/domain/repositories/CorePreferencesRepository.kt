package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.UserTheme
import kotlinx.coroutines.flow.Flow

interface CorePreferencesRepository {
    fun getTheme(): Flow<ThemePreference>
    suspend fun setTheme(theme: ThemePreference)
    fun getColorScheme(): Flow<String>

    /** Switches to the built-in scheme [id], leaving any active imported theme. */
    suspend fun setColorScheme(id: String)

    fun getUserThemes(): Flow<List<UserTheme>>

    /** The imported theme in use, or null when a built-in scheme is. */
    fun getActiveUserTheme(): Flow<UserTheme?>

    /**
     * Parses [json] as a theme, saves it and makes it active. A saved theme with the same name is replaced.
     *
     * @throws IllegalArgumentException when [json] isn't a valid theme.
     */
    suspend fun importUserTheme(json: String): UserTheme
    suspend fun setActiveUserTheme(name: String)
    suspend fun deleteUserTheme(name: String)

    fun getAutoLockMinutes(): Flow<Int>
    suspend fun setAutoLockMinutes(minutes: Int)

    /** User overrides keyed by shortcut id. Shortcuts without an entry use their default chord. */
    fun getShortcuts(): Flow<Map<String, KeyChord>>

    /** Sets the chord for [id], or restores its default when [chord] is null. */
    suspend fun setShortcut(id: String, chord: KeyChord?)
}
