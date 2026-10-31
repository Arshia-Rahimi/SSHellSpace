package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composeunstyled.TooltipHost
import com.composeunstyled.platformtheme.buildPlatformTheme
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.ThemePreference
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground

@Composable
fun DecoratedWindowScope.SSHellSpaceTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    colorScheme: AppColorScheme = AppColorScheme.Default,
    content: @Composable () -> Unit,
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM_DEFAULT -> isSystemInDarkMode()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val theme = buildPlatformTheme {
        name = "SSHellSpaceTheme"
        applyThemeColors(isDark, colorScheme)
        defaultContentColor = themeColors(isDark, colorScheme).getValue(onBackground)
    }

    theme {
        WindowBackground(Theme[colors][background])
        WindowAppearance(if (isDark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
        TooltipHost(Modifier.fillMaxSize()) {
            content()
        }
    }
}
