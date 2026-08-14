package com.github.arshia_rahimi.sshellspace.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.compose_utils.toColor
import com.github.arshia_rahimi.core.data.PreferencesRepository
import com.github.arshia_rahimi.core.model.DarkModeConfig
import com.github.arshia_rahimi.core.model.Theme
import com.materialkolor.DynamicMaterialTheme
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import org.koin.compose.koinInject

@Composable
fun DecoratedWindowScope.AppTheme(content: @Composable () -> Unit) {
    val theme by koinInject<PreferencesRepository>().theme.collectAsStateWithLifecycle(Theme())
    val isDark = theme.darkModeConfig.isDark() || isSystemInDarkTheme()

    DynamicMaterialTheme(
        primary = theme.primary.toColor(),
        isDark = isDark,
        isAmoled = theme.darkModeConfig == DarkModeConfig.AMOLED_DARK,
        animate = true,
    ) {
        WindowBackground(MaterialTheme.colorScheme.background)
        WindowAppearance(if (isDark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
        content()
    }
}
