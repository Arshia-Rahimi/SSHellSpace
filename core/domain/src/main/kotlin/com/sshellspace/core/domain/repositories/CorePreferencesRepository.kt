package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.models.ThemePreference
import kotlinx.coroutines.flow.Flow

interface CorePreferencesRepository {
    fun getTheme(): Flow<ThemePreference>
    suspend fun setTheme(theme: ThemePreference)
    fun getColorScheme(): Flow<String>
    suspend fun setColorScheme(id: String)

    /** User overrides keyed by shortcut id. Shortcuts without an entry use their default chord. */
    fun getShortcuts(): Flow<Map<String, KeyChord>>

    /** Sets the chord for [id], or restores its default when [chord] is null. */
    suspend fun setShortcut(id: String, chord: KeyChord?)
}
