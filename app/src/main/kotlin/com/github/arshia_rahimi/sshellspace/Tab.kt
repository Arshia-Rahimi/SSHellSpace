package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.tabs.NewTabFactory
import com.github.arshia_rahimi.sshellspace.tabs.SettingsTab
import com.github.arshia_rahimi.sshellspace.tabs.VaultTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update


interface Tab {
    val id: TabId
    var component: TabComponent
}

typealias TabId = String

// todo improve async operations
class TabManager(
    settingsTab: SettingsTab,
    vaultTab: VaultTab,
    val newTabFactory: NewTabFactory,
) {
    val initialTab = vaultTab

    val fixedTabs: List<Tab> = listOf(settingsTab, vaultTab)

    private val _tabs: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    val tabs: Flow<List<Tab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow(initialTab.id)
    val currentTabId = _currentTabId.asStateFlow()

    val currentTab: Flow<Tab> = combine(_currentTabId, _tabs) { id, tabs ->
        fixedTabs.find { it.id == id } ?: tabs.find { it.id == id } ?: initialTab
    }

    fun createNewTab() {
        val newTab = newTabFactory.create()
        _tabs.update { _tabs.value + newTab }
        _currentTabId.update { newTab.id }
    }

    fun setCurrentTab(id: TabId) {
        _currentTabId.update { id }
    }

    fun closeTab(tabId: TabId) {
        val tabs = _tabs.value
        val currentTabIndex = tabs.indexOfFirst { it.id == tabId }
        if(currentTabIndex == -1) return
        _currentTabId.update { tabs[currentTabIndex].id }
        _tabs.update { list -> list.filterNot { it.id == tabId } }
    }

    fun moveTab(tabId: TabId, to: Int) {
        _tabs.update { tabs ->
            val index = tabs.indexOfFirst { it.id == tabId }
            if (index == -1) return@update tabs

            tabs.toMutableList().apply {
                add(to.coerceIn(0, size), removeAt(index))
            }
        }
    }
}
