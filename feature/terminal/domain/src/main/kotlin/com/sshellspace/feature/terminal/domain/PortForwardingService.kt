package com.sshellspace.feature.terminal.domain

import com.sshellspace.core.domain.models.PortForward
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow

/** Runs saved port forwards. Forwards on the same connection share one SSH session. */
interface PortForwardingService {
    /** State of every forward that is starting, running or has failed, by forward id. Stopped forwards are absent. */
    val states: StateFlow<Map<Uuid, PortForwardState>>

    /**
     * Connects to the forward's connection unless it is already open, then starts [forward], restarting it if it was
     * running. Login questions go through [prompter]. Returns once the forward runs, fails or the user cancels.
     */
    suspend fun start(forward: PortForward, prompter: SshPrompter)

    /** Stops [forwardId] and closes its session when no other forward uses it. Also clears a failed state. */
    fun stop(forwardId: Uuid)
}

sealed interface PortForwardState {
    data object Starting : PortForwardState
    data class Active(val boundAddress: String) : PortForwardState
    data class Failed(val message: String) : PortForwardState
}
