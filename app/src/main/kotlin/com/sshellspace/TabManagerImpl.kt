package com.sshellspace

import com.sshellspace.core.domain.models.TabParams
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabsState
import com.sshellspace.core.ui.tabs.contains
import com.sshellspace.feature.terminal.domain.models.SftpParams
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.ui.SftpTabFactory
import com.sshellspace.feature.terminal.ui.SshTerminalTabFactory
import com.sshellspace.feature.terminal.ui.TerminalTabFactory
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TabManagerImpl(
    private val defaultTabId: TabId,
    fixedTabs: List<TabItem>,
) : TabManager {
    private val paramsByTab = ConcurrentHashMap<TabId, TabParams>()
    private val _tabsState = MutableStateFlow(TabsState(defaultTabId, fixedTabs))

    override fun tabsState() = _tabsState.asStateFlow()

    override fun createNewTab(params: TabParams) {
        val newTab = when (params) {
            is TerminalParams -> TerminalTabFactory.create(params)
            is SshTerminalParams -> SshTerminalTabFactory.create(params)
            is SftpParams -> SftpTabFactory.create(params)
            else -> throw IllegalArgumentException("Unknown tab param type")
        }
        paramsByTab[newTab.tab.id] = params
        _tabsState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTab.tab.id,
            )
        }
    }

    override fun setCurrentTab(id: TabId) {
        _tabsState.update { state ->
            if (id !in state) return@update state
            state.copy(currentTabId = id)
        }
    }

    override fun closeTab(id: TabId) {
        _tabsState.update { state ->
            val tabIndex = state.tabs.indexOfFirst { it.tab.id == id }
            if (tabIndex == -1) return@update state

            state.tabs[tabIndex].tab.viewModelStore.clear()
            paramsByTab.remove(id)

            val newTabs = state.tabs.toMutableList().apply { removeAt(tabIndex) }

            val newCurrentTabId =
                if (state.currentTabId != id) {
                    state.currentTabId
                } else {
                    newTabs.getOrNull(tabIndex)?.tab?.id
                        ?: newTabs.getOrNull(tabIndex - 1)?.tab?.id
                        ?: state.fixedTab.last().tab.id
                }

            state.copy(
                tabs = newTabs,
                currentTabId = newCurrentTabId,
            )
        }
    }

    override fun reset() {
        _tabsState.update { state ->
            state.tabsSequence.forEach { it.tab.viewModelStore.clear() }
            paramsByTab.clear()
            state.copy(tabs = emptyList(), currentTabId = defaultTabId)
        }
    }

    override fun clearFixedTabs() {
        _tabsState.value.fixedTab.forEach { it.tab.viewModelStore.clear() }
    }

    override fun openTabs(): List<Pair<TabId, TabParams>> =
        _tabsState.value.tabs.mapNotNull { item -> paramsByTab[item.tab.id]?.let { item.tab.id to it } }
}
