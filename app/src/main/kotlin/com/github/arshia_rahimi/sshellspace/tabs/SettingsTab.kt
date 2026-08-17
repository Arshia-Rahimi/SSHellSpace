package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.sshellspace.Tab
import com.github.arshia_rahimi.sshellspace.feature.terminal.SettingsTabComponent

class SettingsTab(
    override var component: SettingsTabComponent,
) : Tab<SettingsTabComponent> {
    override val id = "settings"
}
