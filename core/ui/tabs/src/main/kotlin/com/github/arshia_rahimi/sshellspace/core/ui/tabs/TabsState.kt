package com.github.arshia_rahimi.sshellspace.core.ui.tabs

data class TabsState(
    val fixedTab: List<TabViewModel>,
    val currentTabId: TabId,
    val tabs: List<TabViewModel> = emptyList(),
) {
    val tabsSequence: Sequence<TabViewModel> get() = tabs.asSequence() + fixedTab.asSequence()
    val currentTab: TabViewModel get() = tabsSequence.first { it.id == currentTabId }

    fun getTab(id: TabId): TabViewModel = tabsSequence.find { it.id == id }!!
}
