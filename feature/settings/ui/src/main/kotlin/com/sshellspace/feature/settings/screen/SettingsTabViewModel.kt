package com.sshellspace.feature.settings.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabController
import org.koin.core.annotation.InjectedParam

class SettingsTabViewModel(
    @InjectedParam private val tabController: TabController,
): ViewModel() {

}
