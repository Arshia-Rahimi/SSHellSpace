package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.tabs.NewTabFactory
import com.github.arshia_rahimi.sshellspace.tabs.SettingsTab
import com.github.arshia_rahimi.sshellspace.tabs.VaultTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface Tab<C: TabComponent> {
    val id: TabId
    var component: C
}

typealias TabId = String

data class TabsState(
    val fixedTabs: List<Tab<*>>,
    val tabs: List<Tab<*>> = emptyList(),
    val tabsOrder: List<TabId> = emptyList(),
    val currentTabId: TabId,
) {
    val tabsSequence: Sequence<Tab<*>>
        get() = tabs.asSequence() + fixedTabs.asSequence()

    val currentTab: Tab<*>
        get() = tabsSequence.first { it.id == currentTabId }
}

class TabManager(
    settingsTab: SettingsTab,
    vaultTab: VaultTab,
    val newTabFactory: NewTabFactory,
) {
    private val _tabsState = MutableStateFlow(
        TabsState(listOf(settingsTab, vaultTab), emptyList(), emptyList(), vaultTab.id)
    )
    val tabsState = _tabsState.asStateFlow()

    fun createNewTab() {
        _tabsState.update {
            val newTab = newTabFactory.create()
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
