package com.sshellspace.core.ui.tabs

data class TabsState(
    val fixedTab: List<Tab>,
    val currentTabId: TabId,
    val tabs: List<Tab> = emptyList(),
) {
    val tabsSequence: Sequence<Tab> get() = tabs.asSequence() + fixedTab.asSequence()
    val currentTab: Tab get() = tabsSequence.first { it.id == currentTabId }

    fun getTab(id: TabId): Tab = tabsSequence.find { it.id == id }!!
}
