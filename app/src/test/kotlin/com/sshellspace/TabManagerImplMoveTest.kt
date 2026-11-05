package com.sshellspace

import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import kotlin.test.Test
import kotlin.test.assertEquals

class TabManagerImplMoveTest {
    private object FakeType : TabType

    private val manager = TabManagerImpl("fixed", listOf(TabItem(FakeType, {}, "Vault", tabId = "fixed")))

    private fun ids() = manager.tabsState().value.tabs.map { it.tab.id }

    private fun openThree(): List<String> {
        repeat(3) { manager.createNewTab(TerminalParams(Shell("zsh", "/bin/zsh"))) }
        return ids()
    }

    @Test
    fun `moving a tab changes the order that gets saved, and keeps the current tab`() {
        val (a, b, c) = openThree()

        manager.moveTab(c, 0)

        assertEquals(listOf(c, a, b), ids())
        assertEquals(listOf(c, a, b), manager.openTabs().map { it.first })
        assertEquals(c, manager.tabsState().value.currentTabId)
    }

    @Test
    fun `an index past either end stops at that end`() {
        val (a, b, c) = openThree()

        manager.moveTab(a, 99)
        assertEquals(listOf(b, c, a), ids())

        manager.moveTab(a, -5)
        assertEquals(listOf(a, b, c), ids())
    }

    @Test
    fun `moving a tab that isn't open changes nothing`() {
        val open = openThree()

        manager.moveTab("fixed", 0)

        assertEquals(open, ids())
    }
}
