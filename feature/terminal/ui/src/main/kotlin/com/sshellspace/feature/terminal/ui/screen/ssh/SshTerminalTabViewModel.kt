package com.sshellspace.feature.terminal.ui.screen.ssh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.SESSION_LOST_EXIT_CODE
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.BossTermSshTerminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SshTerminalState {
    data class Connecting(val title: String) : SshTerminalState
    data class AwaitingHostKey(val prompt: HostKeyPrompt) : SshTerminalState
    data class AwaitingSecret(val prompt: SecretPrompt) : SshTerminalState
    data class AwaitingChallenge(val challenge: AuthChallenge) : SshTerminalState
    data class Connected(val terminal: Terminal) : SshTerminalState

    /** The connection dropped or a retry failed, and another attempt starts in [secondsLeft]. */
    data class Reconnecting(
        val title: String,
        val attempt: Int,
        val maxAttempts: Int,
        val secondsLeft: Int,
        val lastError: String? = null,
    ) : SshTerminalState
    data class Failed(val message: String) : SshTerminalState
    data object Cancelled : SshTerminalState
    data class Disconnected(val exitCode: Int?) : SshTerminalState
}

private const val SECOND_MILLIS = 1_000L

sealed interface SshTerminalAction {
    data class AnswerHostKey(val trust: Boolean) : SshTerminalAction
    data class SubmitSecret(val value: String) : SshTerminalAction
    data class SubmitChallenge(val answers: List<String>) : SshTerminalAction

    /** Dismisses the open prompt, or stops connecting if no prompt is open. */
    data object Cancel : SshTerminalAction
    data object Reconnect : SshTerminalAction
}

/** Seconds to wait before each automatic attempt, leaving a flaky network or a rebooting server room to recover. */
internal val RECONNECT_DELAYS_SECONDS = listOf(2, 5, 10, 20, 30)

/**
 * Connects the tab to a saved SSH connection and answers the connection's questions through the UI:
 * each [SshPrompter] call shows a prompt state and suspends until the user responds.
 */
class SshTerminalTabViewModel(
    private val tabController: TabController,
    private val params: SshTerminalParams,
    private val connectionService: SshConnectionService,
    private val terminalPreferences: TerminalPreferencesRepository,
    private val vaultManager: VaultManager,
    private val createTerminal: (ShellSession.Ssh, String?, (Int) -> Unit, (String) -> Unit) -> Terminal =
        ::BossTermSshTerminal,
) : ViewModel(), SshPrompter {

    private val _state = MutableStateFlow<SshTerminalState>(SshTerminalState.Connecting(params.title))
    val state: StateFlow<SshTerminalState> = _state.asStateFlow()

    @Volatile
    private var pendingAnswer: CompletableDeferred<Any?>? = null
    private var connectJob: Job? = null
    private var retryJob: Job? = null
    private var terminal: Terminal? = null

    /** How many automatic attempts have run since the connection dropped. 0 when none are under way. */
    private var retryAttempt = 0

    init {
        connect()
    }

    fun execute(action: SshTerminalAction) {
        when (action) {
            is SshTerminalAction.AnswerHostKey -> answer(action.trust)
            is SshTerminalAction.SubmitSecret -> answer(action.value)
            is SshTerminalAction.SubmitChallenge -> answer(action.answers)
            SshTerminalAction.Cancel -> when {
                pendingAnswer != null -> answer(null)
                retryJob?.isActive == true -> stopRetrying()
                else -> cancelConnecting()
            }
            SshTerminalAction.Reconnect -> {
                retryJob?.cancel()
                connect()
            }
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
            // A connection can drop while the vault is locked, and connecting needs the saved login from the vault.
            vaultManager.activeVault.first { it != null }
            val result = try {
                connectionService.connect(params.connectionId, this@SshTerminalTabViewModel)
            } catch (e: CancellationException) {
                // Is an IllegalStateException too, and has to carry on cancelling this coroutine.
                throw e
            } catch (_: IllegalStateException) {
                // The vault locked partway through reading the login.
                SshConnectResult.Failed("The vault locked while connecting.")
            }
            _state.value = when (result) {
                is SshConnectResult.Success -> {
                    tabController.setTitle(result.connection.name)
                    retryAttempt = 0
                    attachTerminal(result).let(SshTerminalState::Connected)
                }
                SshConnectResult.Cancelled -> {
                    retryAttempt = 0
                    SshTerminalState.Cancelled
                }
                is SshConnectResult.Failed -> if (retryAttempt in 1 until RECONNECT_DELAYS_SECONDS.size) {
                    scheduleRetry(result.message)
                    return@launch
                } else {
                    retryAttempt = 0
                    SshTerminalState.Failed(result.message)
                }
            }
        }
    }

    private fun attachTerminal(result: SshConnectResult.Success): Terminal {
        lateinit var attached: Terminal
        // A terminal being replaced reports its own exit late, which mustn't be taken for the new one's.
        attached = createTerminal(
            result.session,
            result.connection.options.startupCommand,
            { exitCode -> if (terminal === attached) onShellExit(exitCode) },
            { message -> if (terminal === attached) onShellFailed(message) },
        )
        terminal = attached
        tabController.setTextInput(attached::sendText)
        return attached
    }

    /** Counts down to the next attempt, which runs [connect] again. Login questions still go to the user as usual. */
    private fun scheduleRetry(lastError: String?) {
        val delaySeconds = RECONNECT_DELAYS_SECONDS[retryAttempt]
        retryAttempt++
        retryJob = viewModelScope.launch {
            for (secondsLeft in delaySeconds downTo 1) {
                _state.update {
                    SshTerminalState.Reconnecting(
                        title = params.title,
                        attempt = retryAttempt,
                        maxAttempts = RECONNECT_DELAYS_SECONDS.size,
                        secondsLeft = secondsLeft,
                        lastError = lastError,
                    )
                }
                delay(SECOND_MILLIS)
            }
            connect()
        }
    }

    private fun stopRetrying() {
        retryJob?.cancel()
        retryAttempt = 0
        _state.update { SshTerminalState.Cancelled }
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
            // An exit status means the shell ended normally, such as the user typing `exit`.
            if (exitCode == SESSION_LOST_EXIT_CODE && terminalPreferences.get().value.autoReconnect) {
                scheduleRetry(lastError = null)
            } else {
                _state.update { SshTerminalState.Disconnected(exitCode.takeUnless { it == SESSION_LOST_EXIT_CODE }) }
            }
        }
    }

    private fun onShellFailed(message: String) {
        viewModelScope.launch {
            releaseTerminal()
            _state.update { SshTerminalState.Failed("Connected, but couldn't open a shell: $message") }
        }
    }

    private fun releaseTerminal() {
        tabController.setTextInput(null)
        terminal?.clear()
        terminal = null
    }

    override fun onCleared() {
        super.onCleared()
        // Unblocks a MINA thread that may be waiting on the user.
        answer(null)
        connectJob?.cancel()
        retryJob?.cancel()
        releaseTerminal()
    }
}
