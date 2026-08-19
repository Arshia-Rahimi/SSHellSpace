package com.github.arshia_rahimi.sshellspace.core.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TabManager(
    defaultTabId: TabId,
    fixedTabs: List<TabViewModel>,
    val tabViewModelFactory: () -> TabViewModel,
) {
    private val _tabsState = MutableStateFlow(
        TabsState(fixedTabs, emptyList(), emptyList(), defaultTabId)
    )
    val tabsState = _tabsState.asStateFlow()

    fun createNewTab() {
        _tabsState.update {
            val newTab = tabViewModelFactory()
            it.copy(
                tabs = it.tabs + newTab,
                tabsOrder = it.tabsOrder + newTab.id,
                currentTabId = newTab.id,
            )
        }
    }

    fun setCurrentTab(id: TabId) {
        _tabsState.update { state ->
            if (id !in state.tabsSequence.map { it.id }) return@update state
            state.copy(currentTabId = id)
        }
    }

    fun closeTab(tabId: TabId) {
        _tabsState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == tabId }
            if (index == -1) return@update state

            val newTabs = state.tabs.filterNot { it.id == tabId }

            val newCurrentTabId =
                if (state.currentTabId != tabId) {
                    state.currentTabId
                } else {
                    newTabs.getOrNull(index - 1)?.id
                        ?: state.fixedTabs.lastOrNull()?.id
                        ?: return@update state
                }

            state.copy(
                tabs = newTabs,
                tabsOrder = state.tabsOrder - tabId,
                currentTabId = newCurrentTabId,
            )
        }
    }

    fun moveTab(from: TabId, to: TabId) {
        _tabsState.update { state ->
            val tabsOrder = state.tabsOrder.toMutableList()
            tabsOrder.removeAll { it == from }
            val toIndex = tabsOrder.indexOfFirst { it == to }
            tabsOrder.add(toIndex.coerceIn(0, tabsOrder.size), from)
            state.copy(tabsOrder = tabsOrder)
        }
    }
}

data class TabsState(
    val fixedTabs: List<TabViewModel>,
    val tabs: List<TabViewModel> = emptyList(),
    val tabsOrder: List<String> = emptyList(),
    val currentTabId: TabId,
) {
    val tabsSequence: Sequence<TabViewModel>
        get() = tabs.asSequence() + fixedTabs.asSequence()

    val currentTab: TabViewModel
        get() = tabsSequence.first { it.id == currentTabId }
}
