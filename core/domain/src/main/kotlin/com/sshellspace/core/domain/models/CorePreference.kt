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
    /** Terminal themes imported on their own, without any app colors. */
    val terminalThemes: List<TerminalColors> = emptyList(),
    /**
     * Idle minutes before a vault locks itself, for vaults with no [VaultPreference.autoLockMinutes] of their own.
     * 0 turns that off, including locking on sleep. Settings no longer changes this.
     */
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
