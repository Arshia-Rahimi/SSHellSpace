package com.sshellspace.feature.terminal.ui.screen.ssh

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.SESSION_LOST_EXIT_CODE
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SshTerminalTabViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val params = SshTerminalParams(Uuid.random(), "server")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Asks one question through the prompter and reports the answer back as the failure message. */
    private class AskingService(private val question: suspend SshPrompter.() -> Any?) : SshConnectionService {
        override suspend fun connect(connectionId: Uuid, prompter: SshPrompter) =
            SshConnectResult.Failed("answer=${prompter.question()}")
    }

    private val preferences = MutableStateFlow(TerminalPreferences())
    private val terminalPreferences = mockk<TerminalPreferencesRepository> { every { get() } returns preferences }

    private val activeVault = MutableStateFlow<VaultInfo?>(VaultInfo(Uuid.random(), "Work"))
    private val vaultManager = mockk<VaultManager> { every { activeVault } returns this@SshTerminalTabViewModelTest.activeVault }

    private fun viewModel(service: SshConnectionService) =
        SshTerminalTabViewModel(mockk<TabController>(relaxed = true), params, service, terminalPreferences, vaultManager)

    /** Reports each terminal's callbacks so a test can end the session the way a real terminal would. */
    private class FakeTerminal(val onExit: (Int) -> Unit) : Terminal {
        val sent = mutableListOf<Pair<String, Boolean>>()
        override fun clear() = Unit
        override fun sendText(text: String, pressEnter: Boolean) {
            sent += text to pressEnter
        }

        @Composable
        override fun content(modifier: Modifier) = Unit
    }

    private fun connectingService(results: MutableList<SshConnectResult>) = object : SshConnectionService {
        var attempts = 0
        override suspend fun connect(connectionId: Uuid, prompter: SshPrompter): SshConnectResult {
            attempts++
            return results.removeFirstOrNull() ?: awaitCancellation()
        }
    }

    private fun success() = SshConnectResult.Success(
        mockk<ShellSession.Ssh>(relaxed = true),
        SshConnection(params.connectionId, "server", "h", username = "u", auth = SshConnectionAuth.Agent),
    )

    private fun viewModelWithFakeTerminals(service: SshConnectionService, terminals: MutableList<FakeTerminal>) =
        SshTerminalTabViewModel(
            mockk<TabController>(relaxed = true),
            params,
            service,
            terminalPreferences,
            vaultManager,
            { _, _, onExit, _ -> FakeTerminal(onExit).also(terminals::add) },
        )

    @Test
    fun `a dropped connection counts down and reconnects by itself`() = runTest(dispatcher) {
        val terminals = mutableListOf<FakeTerminal>()
        val service = connectingService(mutableListOf(success(), success()))
        val vm = viewModelWithFakeTerminals(service, terminals)
        testScheduler.advanceUntilIdle()
        assertIs<SshTerminalState.Connected>(vm.state.value)

        terminals.single().onExit(SESSION_LOST_EXIT_CODE)
        testScheduler.runCurrent()
        val waiting = assertIs<SshTerminalState.Reconnecting>(vm.state.value)
        assertEquals(1, waiting.attempt)

        testScheduler.advanceUntilIdle()
        assertIs<SshTerminalState.Connected>(vm.state.value)
        assertEquals(2, service.attempts)
    }

    @Test
    fun `a connection lost while locked waits for the unlock before reconnecting`() = runTest(dispatcher) {
        val terminals = mutableListOf<FakeTerminal>()
        val service = connectingService(mutableListOf(success(), success()))
        val vm = viewModelWithFakeTerminals(service, terminals)
        testScheduler.advanceUntilIdle()

        val vault = activeVault.value
        activeVault.value = null
        terminals.single().onExit(SESSION_LOST_EXIT_CODE)
        testScheduler.advanceUntilIdle()
        assertEquals(1, service.attempts)

        activeVault.value = vault
        testScheduler.advanceUntilIdle()
        assertEquals(2, service.attempts)
        assertIs<SshTerminalState.Connected>(vm.state.value)
    }

    @Test
    fun `the vault locking mid-connect fails cleanly`() = runTest(dispatcher) {
        val service = object : SshConnectionService {
            override suspend fun connect(connectionId: Uuid, prompter: SshPrompter): SshConnectResult =
                throw IllegalStateException("No vault is unlocked.")
        }
        val vm = viewModel(service)
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Failed("The vault locked while connecting."), vm.state.value)
    }

    @Test
    fun `typing exit doesn't reconnect`() = runTest(dispatcher) {
        val terminals = mutableListOf<FakeTerminal>()
        val service = connectingService(mutableListOf(success()))
        val vm = viewModelWithFakeTerminals(service, terminals)
        testScheduler.advanceUntilIdle()

        terminals.single().onExit(0)
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Disconnected(0), vm.state.value)
        assertEquals(1, service.attempts)
    }

    @Test
    fun `with reconnecting turned off a dropped connection just closes`() = runTest(dispatcher) {
        preferences.value = TerminalPreferences(autoReconnect = false)
        val terminals = mutableListOf<FakeTerminal>()
        val vm = viewModelWithFakeTerminals(connectingService(mutableListOf(success())), terminals)
        testScheduler.advanceUntilIdle()

        terminals.single().onExit(SESSION_LOST_EXIT_CODE)
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Disconnected(null), vm.state.value)
    }

    @Test
    fun `retrying gives up after the last attempt and shows the error`() = runTest(dispatcher) {
        val terminals = mutableListOf<FakeTerminal>()
        val failures = List(RECONNECT_DELAYS_SECONDS.size) { SshConnectResult.Failed("no route") }
        val service = connectingService((listOf(success()) + failures).toMutableList())
        val vm = viewModelWithFakeTerminals(service, terminals)
        testScheduler.advanceUntilIdle()

        terminals.single().onExit(SESSION_LOST_EXIT_CODE)
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Failed("no route"), vm.state.value)
        assertEquals(1 + RECONNECT_DELAYS_SECONDS.size, service.attempts)
    }

    @Test
    fun `stop cancels the countdown`() = runTest(dispatcher) {
        val terminals = mutableListOf<FakeTerminal>()
        val service = connectingService(mutableListOf(success()))
        val vm = viewModelWithFakeTerminals(service, terminals)
        testScheduler.advanceUntilIdle()
        terminals.single().onExit(SESSION_LOST_EXIT_CODE)
        testScheduler.runCurrent()

        vm.execute(SshTerminalAction.Cancel)
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Cancelled, vm.state.value)
        assertEquals(1, service.attempts)
    }

    @Test
    fun `a replaced terminal reporting its exit late is ignored`() = runTest(dispatcher) {
        val terminals = mutableListOf<FakeTerminal>()
        val vm = viewModelWithFakeTerminals(connectingService(mutableListOf(success(), success())), terminals)
        testScheduler.advanceUntilIdle()
        vm.execute(SshTerminalAction.Reconnect)
        testScheduler.advanceUntilIdle()

        terminals.first().onExit(SESSION_LOST_EXIT_CODE)
        testScheduler.advanceUntilIdle()

        assertEquals(terminals.last(), assertIs<SshTerminalState.Connected>(vm.state.value).terminal)
    }

    @Test
    fun `a secret prompt is shown and the submitted value goes back to the connection`() = runTest(dispatcher) {
        val vm = viewModel(AskingService { askSecret(SecretPrompt("Password", "Enter it", "Password")) })
        testScheduler.advanceUntilIdle()
        assertIs<SshTerminalState.AwaitingSecret>(vm.state.value)

        vm.execute(SshTerminalAction.SubmitSecret("hunter2"))
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Failed("answer=hunter2"), vm.state.value)
    }

    @Test
    fun `cancelling a prompt answers null`() = runTest(dispatcher) {
        val vm = viewModel(AskingService { askSecret(SecretPrompt("Password", "Enter it", "Password")) })
        testScheduler.advanceUntilIdle()

        vm.execute(SshTerminalAction.Cancel)
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Failed("answer=null"), vm.state.value)
    }

    @Test
    fun `rejecting a host key answers false`() = runTest(dispatcher) {
        val vm = viewModel(AskingService { confirmHostKey(HostKeyPrompt("h", 22, "ssh-ed25519", "SHA256:x")) })
        testScheduler.advanceUntilIdle()
        assertIs<SshTerminalState.AwaitingHostKey>(vm.state.value)

        vm.execute(SshTerminalAction.AnswerHostKey(trust = false))
        testScheduler.advanceUntilIdle()

        assertEquals(SshTerminalState.Failed("answer=false"), vm.state.value)
    }

    @Test
    fun `cancel while connecting stops the attempt and reconnect starts a new one`() = runTest(dispatcher) {
        var attempts = 0
        val service = object : SshConnectionService {
            override suspend fun connect(connectionId: Uuid, prompter: SshPrompter): SshConnectResult {
                attempts++
                awaitCancellation()
            }
        }
        val vm = viewModel(service)
        testScheduler.advanceUntilIdle()

        vm.execute(SshTerminalAction.Cancel)
        testScheduler.advanceUntilIdle()
        assertEquals(SshTerminalState.Cancelled, vm.state.value)

        vm.execute(SshTerminalAction.Reconnect)
        testScheduler.advanceUntilIdle()
        assertIs<SshTerminalState.Connecting>(vm.state.value)
        assertEquals(2, attempts)
    }
}
