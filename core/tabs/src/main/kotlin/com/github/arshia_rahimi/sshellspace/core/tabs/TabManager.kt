package com.github.arshia_rahimi.sshellspace.core.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TabManager(
    defaultTabId: TabId,
    fixedTabs: List<TabViewModel>,
    val newTabViewModelFactory: () -> TabViewModel,
) : TabController {
    private val _tabsState = MutableStateFlow(TabsState(fixedTabs, defaultTabId))
    val tabsState = _tabsState.asStateFlow()

    fun createNewTab(): TabId {
        val newTab = newTabViewModelFactory()
        _tabsState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTab.id,
            )
        }
        return newTab.id
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

            val newTabs = state.tabs.toMutableList()
            newTabs.removeAt(index).onClose()

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
                currentTabId = newCurrentTabId,
            )
        }
    }

    override fun replaceTab(tabId: TabId, newViewModel: TabViewModel) {
        _tabsState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == tabId }
            if (index == -1) return@update state
            val newTabs = state.tabs.toMutableList()
            newTabs[index] = newViewModel
            state.copy(tabs = newTabs)
        }
    }
}
