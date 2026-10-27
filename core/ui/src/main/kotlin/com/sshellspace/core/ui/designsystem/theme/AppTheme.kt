package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.composeunstyled.platformtheme.buildPlatformTheme
import com.composeunstyled.theme.Theme
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground

@Composable
fun DecoratedWindowScope.SSHellSpaceTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()

    val theme = buildPlatformTheme {
        name = "SSHellSpaceTheme"
        applyThemeColors(isDark)
        defaultContentColor = themeColors(isDark).getValue(onBackground)
    }

    theme {
        WindowBackground(Theme[colors][background])
        WindowAppearance(if (isDark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
        content()
    }
}
