package com.sshellspace.core.domain.models

data class CorePreference(
    val themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
): Preference

enum class ThemePreference {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK
}
