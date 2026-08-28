package com.sshellspace.core.ui.tabs

data class TabsState(
    val currentTabId: TabId,
    val fixedTab: List<TabItem>,
    val tabs: List<TabItem> = emptyList(),
) {
    val tabsSequence: Sequence<TabItem> get() = tabs.asSequence() + fixedTab.asSequence()
    val currentTab: TabItem get() = getTab(currentTabId) ?: fixedTab.first()

    fun getTab(id: TabId): TabItem? = tabsSequence.find { it.tab.id == id }
}

operator fun TabsState.contains(id: TabId): Boolean =
    tabsSequence.map { it.tab.id }.contains(id)

operator fun TabsState.get(id: TabId): TabItem? =
    tabsSequence.firstOrNull { it.tab.id == id }
