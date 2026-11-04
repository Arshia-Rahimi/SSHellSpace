package com.sshellspace.core.domain.models

import kotlinx.serialization.Serializable

const val DEFAULT_COLOR_SCHEME_ID = "default"
const val DEFAULT_AUTO_LOCK_MINUTES = 15

@Serializable
data class CorePreference(
    val themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    val colorSchemeId: String = DEFAULT_COLOR_SCHEME_ID,
    val shortcuts: Map<String, KeyChord> = emptyMap(),
    val userThemes: List<UserTheme> = emptyList(),
    /** The imported theme in use, which takes over from [colorSchemeId]. Null when using a built-in scheme. */
    val activeUserThemeName: String? = null,
    /** Idle minutes before the vault locks itself. 0 turns that off, including locking on sleep. */
    val autoLockMinutes: Int = DEFAULT_AUTO_LOCK_MINUTES,
)

@Serializable
data class UpdateCorePreference(
    val themePreference: ThemePreference? = null,
    val colorSchemeId: String? = null,
    val shortcuts: Map<String, KeyChord>? = null,
)

enum class ThemePreference {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK
}
