package com.github.arshia_rahimi.sshellspace.core.tabs

interface TabController {
    fun replaceTab(tabId: TabId, newViewModel: TabViewModel)
}
