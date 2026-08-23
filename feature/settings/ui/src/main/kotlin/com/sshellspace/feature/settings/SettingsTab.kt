package com.sshellspace.feature.settings

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabInstance
import com.sshellspace.feature.settings.screen.SettingsTabViewModel

val SettingsTab = Tab(
    qualifier = "SettingsTab",
    viewModelKClass = SettingsTabViewModel::class,
)

data class SettingsTabInstance(
    val from: String,
): TabInstance {
    override val tab = SettingsTab
}
