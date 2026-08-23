package com.sshellspace.core.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Theme(
    val darkModeConfig: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    val primary: Color = Color(0xFFFF0000),
    val secondary: Color? = null,
    val tertiary: Color? = null,
    val neutral: Color? = null,
    val neutralVariant: Color? = null,
    val error: Color? = null,
)

@Serializable
@JvmInline
value class Color(val value: Long)

enum class ThemePreference {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK
}
