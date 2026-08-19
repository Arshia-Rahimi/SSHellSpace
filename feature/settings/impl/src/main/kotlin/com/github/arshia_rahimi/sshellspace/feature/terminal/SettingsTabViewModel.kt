package com.github.arshia_rahimi.sshellspace.feature.terminal

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.sshellspace.core.tabs.TabViewModel

class SettingsTabViewModel: TabViewModel(
    id = "Settings",
    initialTitle = "Settings",
    initialIcon = null,
) {
    @Composable
    override fun screen() = SettingsTabScreen()
}
