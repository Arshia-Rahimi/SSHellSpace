package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.SavedWindow
import com.sshellspace.core.domain.models.TerminalColors
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

    fun getTerminalThemes(): Flow<List<TerminalColors>>

    /**
     * Parses [json] as terminal colors and saves them. A saved theme with the same name is replaced.
     *
     * @param fallbackName the name to use when the file doesn't give one.
     * @throws IllegalArgumentException when [json] isn't a valid terminal theme.
     */
    suspend fun importTerminalTheme(json: String, fallbackName: String): TerminalColors
    suspend fun deleteTerminalTheme(name: String)

    fun getAutoLockMinutes(): Flow<Int>

    fun getAutoUpdate(): Flow<Boolean>
    suspend fun setAutoUpdate(enabled: Boolean)
    fun getUpdateCheckIntervalHours(): Flow<Int>
    suspend fun setUpdateCheckIntervalHours(hours: Int)

    /** When a check for updates last got an answer, in epoch milliseconds. Null if none has. */
    fun getLastUpdateCheck(): Flow<Long?>
    suspend fun setLastUpdateCheck(epochMillis: Long)

    /** Whether the first-run welcome is over. The first value is the stored one, never a placeholder. */
    fun getWelcomeSeen(): Flow<Boolean>
    suspend fun setWelcomeSeen()

    /** The main window as it was last left, or null if it never was. The first value is the stored one. */
    fun getWindow(): Flow<SavedWindow?>
    suspend fun setWindow(window: SavedWindow)

    /** User overrides keyed by shortcut id. Shortcuts without an entry use their default chord. */
    fun getShortcuts(): Flow<Map<String, KeyChord>>

    /** Sets the chord for [id], or restores its default when [chord] is null. */
    suspend fun setShortcut(id: String, chord: KeyChord?)
}
