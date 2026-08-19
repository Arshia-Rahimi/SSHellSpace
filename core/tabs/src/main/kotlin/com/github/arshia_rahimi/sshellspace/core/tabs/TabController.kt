package com.github.arshia_rahimi.sshellspace.core.tabs

import kotlinx.coroutines.flow.StateFlow

interface TabController {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(): TabId
    fun setCurrentTab(tabId: TabId)
    fun closeTab(tabId: TabId)
    fun replaceTab(tabId: TabId, newViewModel: TabViewModel)
}
