package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.ui.Tab
import com.github.arshia_rahimi.core.ui.TabId
import com.github.arshia_rahimi.sshellspace.feature.settings.SettingsTab
import com.github.arshia_rahimi.sshellspace.feature.vault.impl.VaultTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object Tabs {
    private val _currentTabId = MutableStateFlow(VaultTab.id)
    val currentTabId = _currentTabId.asStateFlow()

    val currentTab: Flow<Tab> get() = _currentTabId.combine(tabs) { tabId, tabs ->
        tabs.find { it.id == tabId }!!
    }

    private val tabOrder: MutableStateFlow<List<TabId>> = MutableStateFlow(emptyList())
    private val _tabs: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())

    val fixedTabs: List<Tab> = listOf(SettingsTab, VaultTab)
    val tabs: Flow<List<Tab>> = _tabs.combine(tabOrder) { tabs, tabOrder ->
        tabOrder.map { tabId ->
            tabs.find { it.id == tabId }!!
        }
    }

    fun setCurrentTab(id: TabId) {
        _currentTabId.update { id }
    }

    fun closeTab(tabId: TabId) {
        _tabs.update { list -> list.filterNot { it.id == tabId } }
    }

    fun moveTab(tabId: TabId, to: Int) {
        tabOrder.update { old ->
            old.toMutableList().apply {
                removeAll { it == tabId }
                add(to, old.find { it == tabId }!!)
            }
        }
    }
}
