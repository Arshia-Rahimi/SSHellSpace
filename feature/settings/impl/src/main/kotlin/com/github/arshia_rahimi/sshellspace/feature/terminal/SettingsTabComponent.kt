package com.github.arshia_rahimi.sshellspace.feature.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.TabComponent
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Named
import sshellspace.feature.settings.impl.generated.resources.Res
import sshellspace.feature.settings.impl.generated.resources.settings

@Named("Settings")
class SettingsTabComponent: TabComponent {
    override val title = MutableStateFlow(Res.string.settings)
    override val icon = MutableStateFlow(null)

    @Composable
    override fun content(modifier: Modifier) = SettingsTabContent(modifier)
}