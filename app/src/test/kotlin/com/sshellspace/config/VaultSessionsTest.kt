package com.sshellspace.config

import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.PortForwardingService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class VaultSessionsTest {
    private val tabs = mockk<TabManager>(relaxed = true)
    private val forwards = mockk<PortForwardingService>(relaxed = true)
    private val preferences = MutableStateFlow(TerminalPreferences())
    private val sessions = VaultSessions(
        tabs,
        { forwards },
        mockk<TerminalPreferencesRepository> { every { get() } returns preferences },
    )
    private val work = VaultInfo(Uuid.random(), "Work")
    private val home = VaultInfo(Uuid.random(), "Home")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `locking keeps sessions but clears the pages holding vault data`() = runTest {
        sessions.onUnlocking(work)
        io.mockk.clearMocks(tabs, forwards)

        sessions.onLocking()

        verify { tabs.clearFixedTabs() }
        verify(exactly = 0) { tabs.reset() }
        verify(exactly = 0) { forwards.stopAll() }
    }

    @Test
    fun `unlocking the same vault leaves its sessions running`() = runTest {
        sessions.onUnlocking(work)
        io.mockk.clearMocks(tabs, forwards)

        sessions.onLocking()
        sessions.onUnlocking(work)

        verify(exactly = 0) { tabs.reset() }
        verify(exactly = 0) { forwards.stopAll() }
    }

    @Test
    fun `unlocking a different vault closes the previous vault's sessions`() = runTest {
        sessions.onUnlocking(work)
        sessions.onLocking()
        io.mockk.clearMocks(tabs, forwards)

        sessions.onUnlocking(home)

        verify { tabs.reset() }
        verify { forwards.stopAll() }
    }

    @Test
    fun `with keeping turned off every lock closes everything`() = runTest {
        preferences.value = TerminalPreferences(keepSessionsWhenLocked = false)
        sessions.onUnlocking(work)
        io.mockk.clearMocks(tabs, forwards)

        sessions.onLocking()

        verify { tabs.reset() }
        verify { forwards.stopAll() }
    }

    @Test
    fun `deleting the vault closes its sessions, and only its own`() = runTest {
        sessions.onUnlocking(work)
        io.mockk.clearMocks(tabs, forwards)

        sessions.onDeleted(home.id)
        verify(exactly = 0) { tabs.reset() }

        sessions.onDeleted(work.id)
        verify { tabs.reset() }
        verify { forwards.stopAll() }
    }
}
