package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.model.TabId
import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.feature.settings.SettingsTab
import com.github.arshia_rahimi.sshellspace.feature.vault.impl.VaultTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

object Tabs {
    val fixedTabs: List<TabComponent> = listOf(SettingsTab, VaultTab)

    private val _tabs: MutableStateFlow<List<TabComponent>> = MutableStateFlow(emptyList())
    val tabs: Flow<List<TabComponent>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow(initialTab.id)
    val currentTabId = _currentTabId.asStateFlow()

    val currentTab: Flow<TabComponent> = combine(_currentTabId, _tabs) { id, tabs ->
        fixedTabs.find { it.id == id } ?: tabs.find { it.id == id }!!
    }

    fun setCurrentTab(id: TabId) {
        _currentTabId.update { id }
    }

    fun closeTab(tabId: TabId) {
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

val initialTab: TabComponent = VaultTab
