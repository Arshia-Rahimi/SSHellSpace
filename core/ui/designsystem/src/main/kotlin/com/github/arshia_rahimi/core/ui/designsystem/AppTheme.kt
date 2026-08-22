package com.github.arshia_rahimi.core.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.composeunstyled.platformtheme.bright
import com.composeunstyled.platformtheme.buildPlatformTheme
import com.composeunstyled.theme.Theme
import com.composeunstyled.theme.ThemeBuilder
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken
import com.composeunstyled.theme.buildTheme
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground

@Composable
fun DecoratedWindowScope.SSHellSpaceTheme(content: @Composable () -> Unit) {
    buildPlatformTheme {
        themeAction()
        WindowBackground(Theme[colors][background])
        WindowAppearance(WindowAppearanceMode.Light)
        content()
    }
}

val colors = ThemeProperty<Color>("colors")
val background = ThemeToken<Color>("background")
val primary = ThemeToken<Color>("primary")

private val themeAction: @Composable ThemeBuilder.() -> Unit = {
    properties[colors] = mapOf(
        background to Color.Blue,
        primary to Color.Black,
    )
}
