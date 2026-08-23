package com.sshellspace.feature.settings.screen

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.tabs.TabViewModel

class SettingsTabViewModel: TabViewModel(
    initialTitle = "Settings",
    initialIcon = null,
) {
    @Composable
    override fun screen() = SettingsTabScreen()
}
