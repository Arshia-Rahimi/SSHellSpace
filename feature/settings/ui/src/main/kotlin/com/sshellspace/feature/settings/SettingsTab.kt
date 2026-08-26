package com.sshellspace.feature.settings

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.settings.screen.SettingsTabScreen

object SettingsTabType : TabType

data class SettingsTab(
    val from: String,
) : Tab(
    type = SettingsTabType,
    initialTitle = "Settings",
    initialIcon = null,
) {
    @Composable
    override fun screen() = SettingsTabScreen()
}
