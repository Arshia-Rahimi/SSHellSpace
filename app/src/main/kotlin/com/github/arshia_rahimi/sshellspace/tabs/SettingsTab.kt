package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.Tab
import org.koin.core.annotation.Named

class SettingsTab(
    @Named("Settings") override var component: TabComponent,
) : Tab {
    override val id = "settings"
}
