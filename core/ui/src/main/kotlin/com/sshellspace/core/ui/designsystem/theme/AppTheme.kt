package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.composeunstyled.TooltipHost
import com.composeunstyled.platformtheme.buildPlatformTheme
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.UserTheme
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground

@Composable
fun DecoratedWindowScope.SSHellSpaceTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    colorScheme: AppColorScheme = AppColorScheme.Default,
    userTheme: UserTheme? = null,
    content: @Composable () -> Unit,
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM_DEFAULT -> isSystemInDarkMode()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val palette = remember(isDark, colorScheme, userTheme) {
        userTheme?.colors(isDark) ?: themeColors(isDark, colorScheme)
    }

    val theme = buildPlatformTheme {
        name = "SSHellSpaceTheme"
        properties[colors] = palette
        defaultContentColor = palette.getValue(onBackground)
    }

    theme {
        WindowBackground(Theme[colors][background])
        WindowAppearance(if (isDark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
        TooltipHost(Modifier.fillMaxSize()) {
            content()
        }
    }
}
