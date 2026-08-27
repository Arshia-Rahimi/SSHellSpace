package com.sshellspace.core.ui.tabs

import com.sshellspace.core.domain.models.TabKey
import kotlinx.coroutines.flow.StateFlow

interface TabController {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(key: TabKey)
    fun setCurrentTab(id: TabId)
    fun closeTab(id: TabId)
    fun onNewTabCreated(callback: (TabId) -> Unit)
}
