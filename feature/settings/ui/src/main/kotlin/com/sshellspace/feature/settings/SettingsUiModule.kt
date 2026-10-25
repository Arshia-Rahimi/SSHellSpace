package com.sshellspace.feature.settings

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.settings.screen.SettingsTabViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val SettingsUiModule = module {
    viewModel { (tabController: TabController) -> SettingsTabViewModel(tabController) }
}
