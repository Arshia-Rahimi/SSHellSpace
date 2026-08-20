package com.github.arshia_rahimi.sshellspace.core.tabs

data class TabsState(
    val fixedTabs: List<TabViewModel>,
    val currentTabId: TabId,
    val tabs: List<TabViewModel> = emptyList(),
) {
    val tabsSequence: Sequence<TabViewModel> get() = tabs.asSequence() + fixedTabs.asSequence()
    val currentTab: TabViewModel get() = tabsSequence.first { it.id == currentTabId }

    fun getTab(id: TabId): TabViewModel = tabsSequence.find { it.id == id }!!
}
