package com.sshellspace

import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabInitializer
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabsState
import kotlin.collections.plus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TabManager(
    defaultTabId: TabId,
    fixedTabs: List<TabItem>,
) : TabManager {
    private val _tabsState = MutableStateFlow(
        TabsState(fixedTabs, defaultTabId)
    )

    override fun tabsState() = _tabsState.asStateFlow()

    override fun createNewTab(initializer: TabInitializer): TabId {
        val newTab = initializer.create()
        _tabsState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTab.tab.id,
            )
        }
        return newTab.tab.id
    }

    override fun setCurrentTab(id: TabId) {
        _tabsState.update { state ->
            if (id !in state.tabsSequence.map { it.tab.id }) return@update state
            state.copy(currentTabId = id)
        }
    }

    override fun closeTab(id: TabId) {
        _tabsState.update { state ->
            val index = state.tabs.indexOfFirst { it.tab.id == id }
            if (index == -1) return@update state
            state.tabs[index].tab.viewModelStore.clear()

            val newTabs = state.tabs.toMutableList()

            val newCurrentTabId =
                if (state.currentTabId != id) {
                    state.currentTabId
                } else {
                    newTabs.getOrNull(index - 1)?.tab?.id
                        ?: state.fixedTab.lastOrNull()?.tab?.id
                        ?: return@update state
                }

            state.copy(
                tabs = newTabs,
                currentTabId = newCurrentTabId,
            )
        }
    }
}
