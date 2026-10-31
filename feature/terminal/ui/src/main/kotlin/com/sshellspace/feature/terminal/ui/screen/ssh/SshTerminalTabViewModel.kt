package com.sshellspace.feature.terminal.ui.screen.ssh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.BossTermSshTerminal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SshTerminalState {
    data class Connecting(val title: String) : SshTerminalState
    data class AwaitingHostKey(val prompt: HostKeyPrompt) : SshTerminalState
    data class AwaitingSecret(val prompt: SecretPrompt) : SshTerminalState
    data class AwaitingChallenge(val challenge: AuthChallenge) : SshTerminalState
    data class Connected(val terminal: Terminal) : SshTerminalState
    data class Failed(val message: String) : SshTerminalState
    data object Cancelled : SshTerminalState
    data class Disconnected(val exitCode: Int?) : SshTerminalState
}

sealed interface SshTerminalAction {
    data class AnswerHostKey(val trust: Boolean) : SshTerminalAction
    data class SubmitSecret(val value: String) : SshTerminalAction
    data class SubmitChallenge(val answers: List<String>) : SshTerminalAction

    /** Dismisses the open prompt, or stops connecting if no prompt is open. */
    data object Cancel : SshTerminalAction
    data object Reconnect : SshTerminalAction
}

/**
 * Connects the tab to a saved SSH connection and answers the connection's questions through the UI:
 * each [SshPrompter] call shows a prompt state and suspends until the user responds.
 */
class SshTerminalTabViewModel(
    private val tabController: TabController,
    private val params: SshTerminalParams,
    private val connectionService: SshConnectionService,
) : ViewModel(), SshPrompter {

    private val _state = MutableStateFlow<SshTerminalState>(SshTerminalState.Connecting(params.title))
    val state: StateFlow<SshTerminalState> = _state.asStateFlow()

    @Volatile
    private var pendingAnswer: CompletableDeferred<Any?>? = null
    private var connectJob: Job? = null
    private var terminal: Terminal? = null

    init {
        connect()
    }

    fun execute(action: SshTerminalAction) {
        when (action) {
            is SshTerminalAction.AnswerHostKey -> answer(action.trust)
            is SshTerminalAction.SubmitSecret -> answer(action.value)
            is SshTerminalAction.SubmitChallenge -> answer(action.answers)
            SshTerminalAction.Cancel -> if (pendingAnswer != null) answer(null) else cancelConnecting()
            SshTerminalAction.Reconnect -> connect()
        }
    }

    override suspend fun confirmHostKey(prompt: HostKeyPrompt): Boolean =
        ask(SshTerminalState.AwaitingHostKey(prompt)) as? Boolean ?: false

    override suspend fun askSecret(prompt: SecretPrompt): String? =
        ask(SshTerminalState.AwaitingSecret(prompt)) as? String

    @Suppress("UNCHECKED_CAST")
    override suspend fun answerChallenge(challenge: AuthChallenge): List<String>? =
        ask(SshTerminalState.AwaitingChallenge(challenge)) as? List<String>

    private fun connect() {
        if (connectJob?.isActive == true) return
        releaseTerminal()
        _state.update { SshTerminalState.Connecting(params.title) }
        connectJob = viewModelScope.launch {
            val result = connectionService.connect(params.connectionId, this@SshTerminalTabViewModel)
            _state.value = when (result) {
                is SshConnectResult.Success -> {
                    tabController.setTitle(result.connection.name)
                    val terminal = BossTermSshTerminal(
                        session = result.session,
                        startupCommand = result.connection.options.startupCommand,
                        onExit = ::onShellExit,
                        onShellError = ::onShellFailed,
                    )
                    this@SshTerminalTabViewModel.terminal = terminal
                    SshTerminalState.Connected(terminal)
                }
                SshConnectResult.Cancelled -> SshTerminalState.Cancelled
                is SshConnectResult.Failed -> SshTerminalState.Failed(result.message)
            }
        }
    }

    /** Shows [prompt] and waits for [answer]. Prompts come one at a time from the connection flow. */
    private suspend fun ask(prompt: SshTerminalState): Any? {
        val deferred = CompletableDeferred<Any?>()
        pendingAnswer = deferred
        _state.update { prompt }
        return try {
            deferred.await()
        } finally {
            pendingAnswer = null
            _state.compareAndSet(prompt, SshTerminalState.Connecting(params.title))
        }
    }

    private fun answer(value: Any?) {
        pendingAnswer?.complete(value)
    }

    private fun cancelConnecting() {
        if (connectJob?.isActive != true) return
        connectJob?.cancel()
        _state.update { SshTerminalState.Cancelled }
    }

    private fun onShellExit(exitCode: Int) {
        viewModelScope.launch {
            releaseTerminal()
            _state.update { SshTerminalState.Disconnected(exitCode) }
        }
    }

    private fun onShellFailed(message: String) {
        viewModelScope.launch {
            releaseTerminal()
            _state.update { SshTerminalState.Failed("Connected, but couldn't open a shell: $message") }
        }
    }

    private fun releaseTerminal() {
        terminal?.clear()
        terminal = null
    }

    override fun onCleared() {
        super.onCleared()
        // Unblocks a MINA thread that may be waiting on the user.
        answer(null)
        connectJob?.cancel()
        releaseTerminal()
    }
}
