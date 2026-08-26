package com.sshellspace.core.ui.tabs

import kotlinx.coroutines.flow.StateFlow

interface TabController {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(tab: Tab)
    fun setCurrentTab(id: TabId)
    fun closeTab(id: TabId)
    fun onNewTabCreated(callback: (TabId) -> Unit)
}
