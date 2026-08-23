package com.github.arshia_rahimi.core.ui.designsystem.theme

import androidx.compose.runtime.Composable
import com.composeunstyled.platformtheme.buildPlatformTheme
import com.composeunstyled.theme.Theme
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground

@Composable
fun DecoratedWindowScope.SSHellSpaceTheme(content: @Composable () -> Unit) {
    val theme = buildPlatformTheme {
        applyThemeColors()
    }

    theme {
        WindowBackground(Theme[colors][background])
        WindowAppearance(WindowAppearanceMode.Light)
        content()
    }
}
