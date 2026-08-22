package com.github.arshia_rahimi.sshellspace.feature.settings

import com.github.arshia_rahimi.sshellspace.core.ui.tabs.Tab
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabInstance
import com.github.arshia_rahimi.sshellspace.feature.settings.screen.SettingsTabViewModel

val SettingsTab = Tab(
    qualifier = "SettingsTab",
    viewModelKClass = SettingsTabViewModel::class,
)

data class SettingsTabInstance(
    val from: String,
): TabInstance {
    override val tab = SettingsTab
}
