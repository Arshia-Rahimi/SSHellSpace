package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TabManager(
    defaultTabId: TabId,
    fixedTabs: List<Tab>,
    val tabViewModelFactory: TabViewModelFactory,
) : TabController {
    private val _tabsState = MutableStateFlow(
        TabsState(fixedTabs.map { tabViewModelFactory(it) }, defaultTabId)
    )

    override fun tabsState() = _tabsState.asStateFlow()

    override fun createNewTab(tab: Tab): TabId {
        val newTab = tabViewModelFactory(tab)
        _tabsState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTab.id,
            )
        }
        return newTab.id
    }

    override fun setCurrentTab(id: TabId) {
        _tabsState.update { state ->
            if (id !in state.tabsSequence.map { it.id }) return@update state
            state.copy(currentTabId = id)
        }
    }

    override fun closeTab(id: TabId) {
        _tabsState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == id }
            if (index == -1) return@update state

            val newTabs = state.tabs.toMutableList()
            newTabs.removeAt(index).onClose()

            val newCurrentTabId =
                if (state.currentTabId != id) {
                    state.currentTabId
                } else {
                    newTabs.getOrNull(index - 1)?.id
                        ?: state.fixedTab.lastOrNull()?.id
                        ?: return@update state
                }

            state.copy(
                tabs = newTabs,
                currentTabId = newCurrentTabId,
            )
        }
    }
}
