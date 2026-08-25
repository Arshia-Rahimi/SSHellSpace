package com.sshellspace.feature.settings

import com.sshellspace.core.ui.tabs.Tab
import com.sshellspace.core.ui.tabs.TabInstance

object SettingsTab: Tab

data class SettingsTabInstance(
    val from: String,
): TabInstance {
    override val tab = SettingsTab
}
