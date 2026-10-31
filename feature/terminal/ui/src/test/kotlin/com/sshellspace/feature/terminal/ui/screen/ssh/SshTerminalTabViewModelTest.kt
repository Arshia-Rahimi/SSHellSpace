package com.sshellspace.feature.terminal.ui.screen.ssh

import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
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

    private fun viewModel(service: SshConnectionService) =
        SshTerminalTabViewModel(mockk<TabController>(relaxed = true), params, service)

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
