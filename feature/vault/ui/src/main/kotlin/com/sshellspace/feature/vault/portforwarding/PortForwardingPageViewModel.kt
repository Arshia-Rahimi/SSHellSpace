package com.sshellspace.feature.vault.portforwarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.emit
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.PortForwardingService
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.vault.domain.PortForwardRepository
import com.sshellspace.feature.vault.domain.SavePortForwardResult
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import com.sshellspace.feature.vault.portforwarding.components.DeletePortForwardDialogEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lists, edits, starts and stops port forwards. Starting one may need a login: the questions show one at a time
 * as [PortForwardingPageUiState.login] and each waits until the user answers.
 */
class PortForwardingPageViewModel(
    private val forwardRepository: PortForwardRepository,
    connectionRepository: SshConnectionRepository,
    private val forwardingService: PortForwardingService,
) : ViewModel() {
    private val forwards = forwardRepository.getForwards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    private val connections = connectionRepository.getConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val sidebar = MutableStateFlow<PortForwardSidebarState?>(null)
    private val isSaveInFlight = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val login = MutableStateFlow<LoginPrompt?>(null)

    private val data = combine(forwards, connections, forwardingService.states) { forwards, connections, states ->
        PortForwardingPageUiState(forwards = forwards, connections = connections, states = states)
    }

    val uiState = combine(data, sidebar, isSaveInFlight, errorMessage, login) { data, sidebar, inFlight, error, login ->
        data.copy(sidebar = sidebar, isSaveInFlight = inFlight, errorMessage = error, login = login)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PortForwardingPageUiState())

    /** Read from MINA threads while they wait on a login answer. */
    private val startJobs = ConcurrentHashMap<Uuid, Job>()
    private val loginLock = Mutex()

    @Volatile
    private var pendingAnswer: CompletableDeferred<Any?>? = null

    fun execute(action: PortForwardingPageAction) {
        when (action) {
            PortForwardingPageAction.OpenCreate -> openSidebar(
                PortForwardSidebarState(
                    editingId = null,
                    form = PortForwardForm(connectionId = connections.value.singleOrNull()?.id),
                ),
            )
            is PortForwardingPageAction.Select -> openSidebar(
                PortForwardSidebarState(editingId = action.forward.id, form = action.forward.toForm()),
            )
            PortForwardingPageAction.CloseSidebar -> closeSidebar()
            is PortForwardingPageAction.EditForm -> sidebar.update { it?.copy(form = action.update(it.form)) }
            PortForwardingPageAction.Save -> save(startAfter = false)
            PortForwardingPageAction.SaveAndStart -> save(startAfter = true)
            PortForwardingPageAction.RequestDelete -> requestDelete()
            is PortForwardingPageAction.Start -> start(action.forward)
            is PortForwardingPageAction.Stop -> stop(action.forward.id)
            is PortForwardingPageAction.AnswerHostKey -> answer(action.trust)
            is PortForwardingPageAction.SubmitSecret -> answer(action.value)
            is PortForwardingPageAction.SubmitChallenge -> answer(action.answers)
            PortForwardingPageAction.CancelLogin -> answer(null)
        }
    }

    private fun openSidebar(state: PortForwardSidebarState) {
        errorMessage.update { null }
        sidebar.update { state }
    }

    private fun closeSidebar() {
        errorMessage.update { null }
        sidebar.update { null }
    }

    private fun start(forward: PortForward) {
        if (startJobs[forward.id]?.isActive == true) return
        startJobs[forward.id] = viewModelScope.launch {
            forwardingService.start(forward, ForwardPrompter(forward.id))
        }
    }

    private fun stop(forwardId: Uuid) {
        // The connection flow waits on a MINA thread, which cancelling the job doesn't reach.
        if (login.value?.forwardId == forwardId) answer(null)
        startJobs.remove(forwardId)?.cancel()
        forwardingService.stop(forwardId)
    }

    private fun save(startAfter: Boolean) {
        val state = sidebar.value ?: return
        val withRequired = state.copy(showRequiredErrors = true)
        if (withRequired.form.validate(forwards.value, state.editingId, showRequired = true).hasErrors) {
            sidebar.update { withRequired }
            return
        }
        val name = state.form.name.trim().ifEmpty { uniqueName(state.form.suggestedName, state.editingId) }

        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = forwardRepository.save(state.editingId, state.form.toDraft(name))) {
                is SavePortForwardResult.Success -> {
                    closeSidebar()
                    val wasRunning = forwardingService.states.value[result.forward.id] is PortForwardState.Active
                    if (startAfter || wasRunning) start(result.forward)
                }
                is SavePortForwardResult.Error -> errorMessage.update { result.message }
            }
            isSaveInFlight.update { false }
        }
    }

    private fun uniqueName(base: String, editingId: Uuid?): String {
        val taken = forwards.value.filter { it.id != editingId }.map { it.name.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base ($it)" }
            .first { it.lowercase() !in taken }
    }

    private fun requestDelete() {
        val id = sidebar.value?.editingId ?: return
        val forward = forwards.value.firstOrNull { it.id == id } ?: return
        DeletePortForwardDialogEvent(
            forwardName = forward.name,
            isRunning = forwardingService.states.value[id] is PortForwardState.Active,
            onConfirm = { delete(id) },
        ).emit()
    }

    private fun delete(id: Uuid) {
        stop(id)
        isSaveInFlight.update { true }
        viewModelScope.launch {
            forwardRepository.delete(id)
            isSaveInFlight.update { false }
            if (sidebar.value?.editingId == id) closeSidebar()
        }
    }

    /** Shows [prompt] and waits for [answer]. Questions from forwards starting at once queue up. */
    private suspend fun ask(prompt: LoginPrompt): Any? = loginLock.withLock {
        // Stopped while waiting in the queue.
        if (startJobs[prompt.forwardId]?.isActive != true) return@withLock null
        val deferred = CompletableDeferred<Any?>()
        pendingAnswer = deferred
        login.update { prompt }
        try {
            deferred.await()
        } finally {
            pendingAnswer = null
            login.compareAndSet(prompt, null)
        }
    }

    private fun answer(value: Any?) {
        pendingAnswer?.complete(value)
    }

    override fun onCleared() {
        super.onCleared()
        // Unblocks a MINA thread that may be waiting on the user. Running forwards keep running.
        answer(null)
    }

    private inner class ForwardPrompter(private val forwardId: Uuid) : SshPrompter {
        override suspend fun confirmHostKey(prompt: HostKeyPrompt): Boolean =
            ask(LoginPrompt.HostKey(forwardId, prompt)) as? Boolean ?: false

        override suspend fun askSecret(prompt: SecretPrompt): String? =
            ask(LoginPrompt.Secret(forwardId, prompt)) as? String

        @Suppress("UNCHECKED_CAST")
        override suspend fun answerChallenge(challenge: AuthChallenge): List<String>? =
            ask(LoginPrompt.Challenge(forwardId, challenge)) as? List<String>
    }
}
