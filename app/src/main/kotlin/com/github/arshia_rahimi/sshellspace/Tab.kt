package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.tabs.NewTabFactory
import com.github.arshia_rahimi.sshellspace.tabs.SettingsTab
import com.github.arshia_rahimi.sshellspace.tabs.VaultTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface Tab {
    val id: TabId
    var component: TabComponent
}

typealias TabId = String

data class TabsState(
    val fixedTabs: List<Tab>,
    val tabs: List<Tab> = emptyList(),
    val currentTabId: TabId,
) {
    val tabsSequence: Sequence<Tab>
        get() = tabs.asSequence().plus(fixedTabs.asSequence())

    val currentTab: Tab
        get() = tabsSequence.first { it.id == currentTabId }
}

class TabManager(
    settingsTab: SettingsTab,
    vaultTab: VaultTab,
    val newTabFactory: NewTabFactory,
) {
    private val _tabsState = MutableStateFlow(
        TabsState(listOf(settingsTab, vaultTab), emptyList(), vaultTab.id)
    )
    val tabsState = _tabsState.asStateFlow()

    fun createNewTab() {
        _tabsState.update {
            val newTab = newTabFactory.create()
            it.copy(
                tabs = it.tabs + newTab,
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
                currentTabId = newCurrentTabId,
            )
        }
    }

    fun moveTab(from: Int, to: Int) {
        _tabsState.update { state ->
            val tabs = state.tabs.toMutableList()
            val tab = tabs.removeAt(from)
            tabs.add(to.coerceIn(0, tabs.size), tab)
            state.copy(tabs = tabs)
        }
    }
}
