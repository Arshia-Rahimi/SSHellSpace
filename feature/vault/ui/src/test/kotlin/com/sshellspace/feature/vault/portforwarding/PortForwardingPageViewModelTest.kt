package com.sshellspace.feature.vault.portforwarding

import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.PortForwardingService
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.vault.domain.PortForwardDraft
import com.sshellspace.feature.vault.domain.PortForwardRepository
import com.sshellspace.feature.vault.domain.SavePortForwardResult
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardingPageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connection = SshConnection(
        id = Uuid.random(),
        name = "server",
        host = "example.com",
        username = "root",
        auth = SshConnectionAuth.Agent,
    )
    private val existing = PortForward(
        id = Uuid.random(),
        name = "postgres",
        connectionId = connection.id,
        type = PortForwardType.Local,
        bindPort = 15432,
        destinationHost = "localhost",
        destinationPort = 5432,
    )
    private val saved = mutableListOf<PortForwardDraft>()
    private val forwardRepository = mockk<PortForwardRepository>(relaxUnitFun = true).apply {
        every { getForwards() } returns flowOf(listOf(existing))
        coEvery { save(any(), any()) } answers {
            val draft = secondArg<PortForwardDraft>().also(saved::add)
            SavePortForwardResult.Success(
                existing.copy(id = firstArg<Uuid?>() ?: Uuid.random(), name = draft.name, bindPort = draft.bindPort),
            )
        }
    }
    private val connectionRepository = mockk<SshConnectionRepository>().apply {
        every { getConnections() } returns flowOf(listOf(connection))
    }
    private val service = FakeForwarding()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        PortForwardingPageViewModel(forwardRepository, connectionRepository, service).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun `a new forward picks the only connection and gets a name when left empty`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.execute(PortForwardingPageAction.OpenCreate)
        testScheduler.advanceUntilIdle()
        assertEquals(connection.id, vm.uiState.value.sidebar?.form?.connectionId)

        vm.execute(PortForwardingPageAction.EditForm { it.copy(bindPort = "8080", destinationPort = "80") })
        vm.execute(PortForwardingPageAction.SaveAndStart)
        testScheduler.advanceUntilIdle()

        assertEquals("8080 → localhost:80", saved.single().name)
        assertEquals(8080, service.started.single().bindPort)
        assertNull(vm.uiState.value.sidebar)
    }

    @Test
    fun `saving without the required fields shows errors instead`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.execute(PortForwardingPageAction.OpenCreate)
        vm.execute(PortForwardingPageAction.Save)
        testScheduler.advanceUntilIdle()

        assertEquals("Port is required", vm.uiState.value.formErrors.bind)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun `saving a running forward restarts it`() = runTest(dispatcher) {
        service.states.value = mapOf(existing.id to PortForwardState.Active("127.0.0.1:15432"))
        val vm = viewModel()

        vm.execute(PortForwardingPageAction.Select(existing))
        vm.execute(PortForwardingPageAction.EditForm { it.copy(bindPort = "25432") })
        vm.execute(PortForwardingPageAction.Save)
        testScheduler.advanceUntilIdle()

        assertEquals(existing.id, service.started.single().id)
        assertEquals(25432, service.started.single().bindPort)
    }

    @Test
    fun `login questions show on the page and the answer goes back`() = runTest(dispatcher) {
        service.question = { askSecret(SecretPrompt("Password", "Enter it", "Password")) }
        val vm = viewModel()

        vm.execute(PortForwardingPageAction.Start(existing))
        testScheduler.advanceUntilIdle()
        assertIs<LoginPrompt.Secret>(vm.uiState.value.login)

        vm.execute(PortForwardingPageAction.SubmitSecret("hunter2"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<Any?>("hunter2"), service.answers)
        assertNull(vm.uiState.value.login)
    }

    @Test
    fun `stopping a forward that waits on a login answers null`() = runTest(dispatcher) {
        service.question = { askSecret(SecretPrompt("Password", "Enter it", "Password")) }
        val vm = viewModel()
        vm.execute(PortForwardingPageAction.Start(existing))
        testScheduler.advanceUntilIdle()

        vm.execute(PortForwardingPageAction.Stop(existing))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<Any?>(null), service.answers)
        assertEquals(listOf(existing.id), service.stopped)
        assertNull(vm.uiState.value.login)
    }

    private class FakeForwarding : PortForwardingService {
        override val states = MutableStateFlow<Map<Uuid, PortForwardState>>(emptyMap())
        var question: (suspend SshPrompter.() -> Any?)? = null
        val started = mutableListOf<PortForward>()
        val stopped = mutableListOf<Uuid>()
        val answers = mutableListOf<Any?>()

        override suspend fun start(forward: PortForward, prompter: SshPrompter) {
            started += forward
            // Like a MINA thread, the question isn't cancelled along with the caller.
            question?.let { ask -> withContext(NonCancellable) { answers += prompter.ask() } }
        }

        override fun stop(forwardId: Uuid) {
            stopped += forwardId
        }

        override fun stopAll() = Unit
    }
}
