package com.github.arshia_rahimi.core.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.data.models.DarkModeConfig
import com.github.arshia_rahimi.core.data.models.Theme
import com.github.arshia_rahimi.core.data.repositories.PreferencesRepository
import com.github.arshia_rahimi.core.ui.common_compose.toColor
import com.materialkolor.DynamicMaterialTheme
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import org.koin.compose.koinInject

@Composable
fun DecoratedWindowScope.AppTheme(content: @Composable () -> Unit) {
    val theme by koinInject<PreferencesRepository>().getTheme().collectAsStateWithLifecycle(Theme())
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
