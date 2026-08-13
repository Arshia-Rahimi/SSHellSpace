package com.github.arshia_rahimi.sshellspace.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.Tab
import sshellspace.feature.settings.impl.generated.resources.Res
import sshellspace.feature.settings.impl.generated.resources.settings

object SettingsTab: Tab {
    override val id: String = "Settings"

    override val title: String
        @Composable get() = Res.string.settings.asString()

    @Composable
    override fun content(modifier: Modifier) = SettingsTabContent(modifier)
}
