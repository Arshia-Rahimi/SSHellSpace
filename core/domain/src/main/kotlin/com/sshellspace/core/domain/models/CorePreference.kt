package com.sshellspace.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class CorePreference(
    val themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
): Preference

@Serializable
data class UpdateCorePreference(
    val themePreference: ThemePreference? = null,
)

enum class ThemePreference {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK
}
