package com.sshellspace

import com.sshellspace.core.domain.models.TabParams
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabsState
import com.sshellspace.core.ui.tabs.contains
import com.sshellspace.core.ui.tabs.get
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.ui.TerminalTabFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TabManagerImpl(
    defaultTabId: TabId,
    fixedTabs: List<TabItem>,
) : TabManager {
    private var onNewTabCreated: ((TabId) -> Unit)? = null
    private val _tabsState = MutableStateFlow(TabsState(defaultTabId, fixedTabs))

    override fun tabsState() = _tabsState.asStateFlow()

    override fun createNewTab(params: TabParams) {
        val newTab = when (params) {
            is TerminalParams -> TerminalTabFactory.create(params)
            else -> throw IllegalArgumentException("Unknown tab param type")
        }
        _tabsState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTab.tab.id,
            )
        }
        onNewTabCreated?.invoke(newTab.tab.id)
    }

    override fun setCurrentTab(id: TabId) {
        _tabsState.update { state ->
            if (id !in state) return@update state
            state.copy(currentTabId = id)
        }
    }

    override fun closeTab(id: TabId) {
        _tabsState.update { state ->
            val tabItem = state[id] ?: return@update state
            tabItem.tab.viewModelStore.clear()

            val newTabs = state.tabs.toMutableList()

            val newCurrentTabId =
                if (state.currentTabId != id) {
                    state.currentTabId
                } else {
                    val index = state.tabsSequence.indexOf(tabItem)
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

    override fun onNewTabCreated(callback: (TabId) -> Unit) {
        onNewTabCreated = callback
    }
}
