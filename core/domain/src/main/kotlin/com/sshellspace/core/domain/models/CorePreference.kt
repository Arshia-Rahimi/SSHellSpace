package com.sshellspace.core.domain.models

import kotlinx.serialization.Serializable

const val DEFAULT_COLOR_SCHEME_ID = "default"

@Serializable
data class CorePreference(
    val themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    val colorSchemeId: String = DEFAULT_COLOR_SCHEME_ID,
    val shortcuts: Map<String, KeyChord> = emptyMap(),
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
