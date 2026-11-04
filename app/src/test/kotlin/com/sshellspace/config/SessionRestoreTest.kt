package com.sshellspace.config

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.TabParams
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.core.ui.tabs.TabType
import com.sshellspace.core.ui.tabs.TabsState
import com.sshellspace.feature.terminal.domain.SavedSessionRepository
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.SavedSession
import com.sshellspace.feature.terminal.domain.models.SavedTab
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import com.sshellspace.feature.vault.domain.SshConnectionDraft
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRestoreTest {
    private object FakeType : TabType

    private class FakeTabs : TabManager {
        private val fixed = TabItem(FakeType, {}, "Vault", tabId = "fixed")
        private val state = MutableStateFlow(TabsState("fixed", listOf(fixed)))
        private val open = mutableListOf<Pair<TabId, TabParams>>()
        override fun tabsState() = state
        override fun createNewTab(params: TabParams) {
            val item = TabItem(FakeType, {}, "tab")
            open += item.tab.id to params
            state.value = state.value.copy(tabs = state.value.tabs + item, currentTabId = item.tab.id)
        }

        override fun setCurrentTab(id: TabId) {
            state.value = state.value.copy(currentTabId = id)
        }

        override fun closeTab(id: TabId) {
            open.removeAll { it.first == id }
            state.value = state.value.copy(tabs = state.value.tabs.filterNot { it.tab.id == id }, currentTabId = "fixed")
        }

        override fun reset() {
            open.clear()
            state.value = state.value.copy(tabs = emptyList(), currentTabId = "fixed")
        }

        override fun clearFixedTabs() = Unit

        override fun openTabs() = open.toList()
    }

    private val server = SshConnection(Uuid.random(), "server", "h", username = "u", auth = SshConnectionAuth.Agent)
    private val shell = Shell("zsh", "/bin/zsh")
    private val activeVault = MutableStateFlow<VaultInfo?>(null)
    private val tabs = FakeTabs()
    private var stored = SavedSession()
    private val preferences = MutableStateFlow(TerminalPreferences())

    private val restore = SessionRestore(
        vaultManager = mockk<VaultManager> { every { activeVault } returns this@SessionRestoreTest.activeVault },
        tabManager = tabs,
        savedSessions = object : SavedSessionRepository {
            override suspend fun load() = stored
            override suspend fun save(session: SavedSession) {
                stored = session
            }
        },
        terminalPreferences = mockk<TerminalPreferencesRepository> { every { get() } returns preferences },
        connections = object : SshConnectionRepository {
            override fun getConnections() = MutableStateFlow(listOf(server))
            override suspend fun get(id: Uuid) = server.takeIf { it.id == id }
            override suspend fun save(id: Uuid?, draft: SshConnectionDraft) = error("Not used")
            override suspend fun delete(id: Uuid) = Unit
        },
        shellDiscovery = mockk<ShellDiscoveryService> { every { discover() } returns listOf(shell) },
    )

    private val vault = VaultInfo(Uuid.random(), "Work")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.running() =
        launch(UnconfinedTestDispatcher(testScheduler)) { restore.run() }

    @Test
    fun `open tabs are saved and come back on the next unlock`() = runTest {
        val job = running()
        activeVault.value = vault
        testScheduler.runCurrent()
        tabs.createNewTab(SshTerminalParams(server.id, "server"))
        tabs.createNewTab(TerminalParams(shell))
        testScheduler.runCurrent()

        assertEquals(listOf(SavedTab.Ssh(server.id, "server"), SavedTab.Local(shell)), stored.tabs)
        assertEquals(1, stored.currentIndex)

        // Locking marks the vault locked, then closes the tabs. That must not overwrite what was saved.
        activeVault.value = null
        tabs.reset()
        testScheduler.runCurrent()
        assertEquals(2, stored.tabs.size)

        activeVault.value = vault
        testScheduler.runCurrent()
        assertEquals(
            listOf<TabParams>(SshTerminalParams(server.id, "server"), TerminalParams(shell)),
            tabs.openTabs().map { it.second },
        )
        assertEquals(tabs.openTabs()[1].first, tabs.tabsState().value.currentTabId)
        job.cancel()
    }

    @Test
    fun `tabs that kept running through a lock aren't opened twice`() = runTest {
        val job = running()
        activeVault.value = vault
        testScheduler.runCurrent()
        tabs.createNewTab(TerminalParams(shell))
        testScheduler.runCurrent()
        val before = tabs.openTabs()

        activeVault.value = null
        testScheduler.runCurrent()
        activeVault.value = vault
        testScheduler.runCurrent()

        assertEquals(before, tabs.openTabs())
        job.cancel()
    }

    @Test
    fun `closing a tab yourself keeps it closed`() = runTest {
        val job = running()
        activeVault.value = vault
        testScheduler.runCurrent()
        tabs.createNewTab(TerminalParams(shell))
        testScheduler.runCurrent()

        tabs.closeTab(tabs.openTabs().single().first)
        testScheduler.runCurrent()

        assertEquals(emptyList(), stored.tabs)
        job.cancel()
    }

    @Test
    fun `a saved tab whose connection was deleted is dropped`() = runTest {
        stored = SavedSession(listOf(SavedTab.Ssh(Uuid.random(), "gone"), SavedTab.Local(shell)), currentIndex = 0)
        val job = running()

        activeVault.value = vault
        testScheduler.runCurrent()

        assertEquals(listOf<TabParams>(TerminalParams(shell)), tabs.openTabs().map { it.second })
        job.cancel()
    }

    @Test
    fun `a saved shell this computer doesn't offer never runs`() = runTest {
        stored = SavedSession(listOf(SavedTab.Local(Shell("evil", "/tmp/evil.sh")), SavedTab.Local(shell)))
        val job = running()

        activeVault.value = vault
        testScheduler.runCurrent()

        assertEquals(listOf<TabParams>(TerminalParams(shell)), tabs.openTabs().map { it.second })
        job.cancel()
    }

    @Test
    fun `with restoring turned off nothing is reopened or saved`() = runTest {
        preferences.value = TerminalPreferences(restoreTabs = false)
        stored = SavedSession(listOf(SavedTab.Local(shell)))
        val job = running()

        activeVault.value = vault
        testScheduler.runCurrent()
        assertEquals(emptyList(), tabs.openTabs())

        tabs.createNewTab(TerminalParams(Shell("bash", "/bin/bash")))
        testScheduler.runCurrent()
        assertEquals(listOf(SavedTab.Local(shell)), stored.tabs)
        job.cancel()
    }
}
