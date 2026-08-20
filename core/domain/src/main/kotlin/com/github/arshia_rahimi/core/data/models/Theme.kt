package com.github.arshia_rahimi.core.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Theme(
    val darkModeConfig: DarkModeConfig = DarkModeConfig.SYSTEM_DEFAULT,
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

enum class DarkModeConfig {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK,
    AMOLED_DARK;

    fun isDark(): Boolean = this == DARK || this == AMOLED_DARK
}
