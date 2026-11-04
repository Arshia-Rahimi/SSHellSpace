package com.sshellspace.core.ui.tabs

import com.sshellspace.core.domain.models.TabParams
import kotlinx.coroutines.flow.StateFlow

interface TabManager {
    fun tabsState(): StateFlow<TabsState>
    fun createNewTab(params: TabParams)
    fun setCurrentTab(id: TabId)
    fun closeTab(id: TabId)

    /** Closes every tab and clears the ViewModels of the fixed ones, back to how the app started. */
    fun reset()
    fun onNewTabCreated(callback: (TabId) -> Unit)
}
