package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeBuilder
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken

val colors = ThemeProperty<Color>("colors")
val background = ThemeToken<Color>("background")
val primary = ThemeToken<Color>("primary")

internal fun ThemeBuilder.applyThemeColors() {
    properties[colors] = mapOf(
        background to Color.Blue,
        primary to Color.Black,
    )
}
