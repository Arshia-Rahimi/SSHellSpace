package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import kotlinx.coroutines.flow.StateFlow

interface TabController {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(tab: Tab): TabId
    fun setCurrentTab(id: TabId)
    fun closeTab(id: TabId)
}
