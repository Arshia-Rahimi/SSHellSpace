package com.sshellspace.core.ui.tabs

import com.sshellspace.core.domain.models.TabParams
import kotlinx.coroutines.flow.StateFlow

interface TabManager {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(params: TabParams)
    fun setCurrentTab(id: TabId)
    fun closeTab(id: TabId)
    fun onNewTabCreated(callback: (TabId) -> Unit)
}
