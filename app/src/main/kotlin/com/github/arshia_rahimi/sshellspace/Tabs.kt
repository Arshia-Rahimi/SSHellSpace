package com.github.arshia_rahimi.sshellspace

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.core.ui.Tab
import com.github.arshia_rahimi.core.ui.TabId
import com.github.arshia_rahimi.sshellspace.feature.settings.SettingsTab
import com.github.arshia_rahimi.sshellspace.feature.vault.impl.VaultTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

object Tabs {
    private val tabOrder: MutableStateFlow<List<TabId>> = MutableStateFlow(listOf("id", "id2"))

    private val _tabs: MutableStateFlow<List<Tab>> = MutableStateFlow(listOf(
        object : Tab {
            override val id: TabId
                get() = "id"
            override val title: String
                @Composable get() = "Creatable"

            @Composable
            override fun content() {
                TODO("Not yet implemented")
            }
        },
        object : Tab {
            override val id: TabId
                get() = "id2"
            override val title: String
                @Composable get() = "Creatable 2"

            @Composable
            override fun content() {
                TODO("Not yet implemented")
            }
        },
    ))

    val fixedTabs: List<Tab> = listOf(SettingsTab, VaultTab)

    val tabs: Flow<List<Tab>> = _tabs.combine(tabOrder) { tabs, tabOrder ->
        tabOrder.map { tabId ->
            tabs.find { it.id == tabId }!!
        }
    }

    private val _currentTabId = MutableStateFlow(VaultTab.id)
    val currentTabId = _currentTabId.asStateFlow()

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
