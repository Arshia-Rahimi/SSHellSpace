package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

typealias TabId = String

class TabManager(
    defaultTabId: TabId,
    fixedTabs: List<TabInstance>,
    private val tabViewModelFactory: TabViewModelFactory,
) : TabController {
    private var onNewTabCreated: ((TabId) -> Unit)? = null

    private val _tabsState = MutableStateFlow(
        TabsState(fixedTabs.map(tabViewModelFactory::create), defaultTabId)
    )

    override fun tabsState() = _tabsState.asStateFlow()

    override fun createNewTab(tabInstance: TabInstance) {
        val newTab = tabViewModelFactory.create(tabInstance)
        _tabsState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTab.id,
            )
        }
        onNewTabCreated?.invoke(newTab.id)
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

    override fun onNewTabCreated(callback: (TabId) -> Unit) {
        onNewTabCreated = callback
    }
}
