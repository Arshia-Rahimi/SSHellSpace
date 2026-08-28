package com.sshellspace.feature.settings

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.tabs.TabInitializer
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.settings.screen.SettingsTabScreen
import com.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object SettingsTab: TabType

fun settingsTabInitializer() = TabInitializer {
    TabItem(
        type = SettingsTab,
        initialTitle = "Settings",
        contentProvider = { item ->
            val viewModel: SettingsTabViewModel = koinViewModel { parametersOf(item as TabController) }
            SettingsTabScreen(viewModel)
        }
    )
}
