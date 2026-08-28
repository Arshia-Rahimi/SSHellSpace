package com.sshellspace.core.ui.tabs

import kotlinx.coroutines.flow.StateFlow

interface TabManager {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(initializer: TabInitializer): TabId
    fun setCurrentTab(id: TabId)
    fun closeTab(id: TabId)
}
