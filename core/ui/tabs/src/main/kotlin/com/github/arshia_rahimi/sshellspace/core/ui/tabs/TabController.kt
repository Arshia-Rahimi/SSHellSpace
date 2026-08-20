package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import kotlinx.coroutines.flow.StateFlow

interface TabController {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(tabType: TabType): TabId
    fun setCurrentTab(tabId: TabId)
    fun closeTab(tabId: TabId)
}
