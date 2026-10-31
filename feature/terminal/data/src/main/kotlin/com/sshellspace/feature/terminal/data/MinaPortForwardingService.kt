package com.sshellspace.feature.terminal.data

import com.sshellspace.core.data.PortForwardDatasource
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.PortForwardingService
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshForwardHandle
import com.sshellspace.feature.terminal.domain.SshPrompter
import java.net.BindException
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MinaPortForwardingService(
    private val connectionService: SshConnectionService,
    forwardDatasource: PortForwardDatasource,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PortForwardingService {

    private val _states = MutableStateFlow<Map<Uuid, PortForwardState>>(emptyMap())
    override val states: StateFlow<Map<Uuid, PortForwardState>> = _states.asStateFlow()

    /** Guards [sessions] and [running]. Never held while connecting, which can wait on the user. */
    private val lock = Any()
    private val sessions = mutableMapOf<Uuid, OpenSession>()
    private val running = mutableMapOf<Uuid, Running>()

    /** One connect at a time per connection, so forwards started together share a session. */
    private val connectLocks = mutableMapOf<Uuid, Mutex>()

    init {
        // A deleted forward (or one deleted with its connection) shouldn't keep running out of sight.
        scope.launch {
            forwardDatasource.index().collect { forwards ->
                val ids = forwards.mapTo(mutableSetOf()) { it.id }
                _states.value.keys.filter { it !in ids }.forEach(::stop)
            }
        }
    }

    override suspend fun start(forward: PortForward, prompter: SshPrompter) {
        stop(forward.id)
        _states.update { it + (forward.id to PortForwardState.Starting) }
        var session: OpenSession? = null
        try {
            session = acquireSession(forward, prompter) ?: return release(forward.id, null, clearState = true)
            val handle = runInterruptible(Dispatchers.IO) { session.ssh.startForward(forward) }
            val stillWanted = synchronized(lock) {
                (_states.value[forward.id] == PortForwardState.Starting).also { wanted ->
                    if (wanted) running[forward.id] = Running(session, handle)
                }
            }
            if (!stillWanted) {
                runCatching { handle.close() }
                return release(forward.id, session, clearState = false)
            }
            _states.update { it + (forward.id to PortForwardState.Active(handle.boundAddress)) }
        } catch (e: CancellationException) {
            release(forward.id, session, clearState = true)
            throw e
        } catch (e: ForwardFailure) {
            fail(forward.id, session, e.message)
        } catch (e: Exception) {
            fail(forward.id, session, describe(e, forward))
        }
    }

    override fun stop(forwardId: Uuid) {
        val stopped = synchronized(lock) { running.remove(forwardId) }
        stopped?.let { runCatching { it.handle.close() } }
        release(forwardId, stopped?.session, clearState = true)
    }

    /** The open session of the forward's connection, connecting first if needed. Null when the user cancelled. */
    private suspend fun acquireSession(forward: PortForward, prompter: SshPrompter): OpenSession? {
        val connectLock = synchronized(lock) { connectLocks.getOrPut(forward.connectionId) { Mutex() } }
        return connectLock.withLock {
            synchronized(lock) { sessions[forward.connectionId]?.also { it.users += forward.id } }
                ?: when (val result = connectionService.connect(forward.connectionId, prompter)) {
                    is SshConnectResult.Success -> OpenSession(forward.connectionId, result.connection.name, result.session)
                        .also { session ->
                            synchronized(lock) {
                                session.users += forward.id
                                sessions[forward.connectionId] = session
                            }
                            result.session.onClose { onSessionClosed(session) }
                        }
                    SshConnectResult.Cancelled -> null
                    is SshConnectResult.Failed -> throw ForwardFailure(result.message)
                }
        }
    }

    private fun fail(forwardId: Uuid, session: OpenSession?, message: String) {
        release(forwardId, session, clearState = false)
        _states.update { it + (forwardId to PortForwardState.Failed(message)) }
    }

    /** Drops [forwardId] as a user of [session] and closes the session once nothing uses it. */
    private fun release(forwardId: Uuid, session: OpenSession?, clearState: Boolean) {
        if (clearState) _states.update { it - forwardId }
        val unused = session?.let {
            synchronized(lock) {
                it.users -= forwardId
                (it.users.isEmpty() && sessions[it.connectionId] === it).also { unused ->
                    if (unused) sessions.remove(it.connectionId)
                }
            }
        }
        if (unused == true) runCatching { session.ssh.close() }
    }

    /** The server or the network ended the session: every forward on it stops. */
    private fun onSessionClosed(session: OpenSession) {
        val dropped = synchronized(lock) {
            if (sessions[session.connectionId] === session) sessions.remove(session.connectionId)
            running.filterValues { it.session === session }.keys.onEach(running::remove)
        }
        if (dropped.isEmpty()) return
        val failed = PortForwardState.Failed("The connection to ${session.name} closed.")
        _states.update { states -> states + dropped.filter { it in states }.associateWith { failed } }
    }

    private fun describe(e: Throwable, forward: PortForward): String {
        val endpoint = "${forward.bindHost}:${forward.bindPort}"
        val reason = generateSequence(e) { it.cause }.mapNotNull { it.message }.lastOrNull() ?: e.javaClass.simpleName
        return when {
            generateSequence(e) { it.cause }.any { it is BindException } -> when {
                reason.contains("permission", ignoreCase = true) ->
                    "Not allowed to listen on $endpoint. Ports below 1024 need admin rights; pick a higher one."
                else -> "$endpoint is already in use."
            }
            forward.type == PortForwardType.Remote ->
                "The server refused to listen on $endpoint. Check that AllowTcpForwarding is on in its sshd_config " +
                    "and the port is free. ($reason)"
            else -> "Couldn't start the forward: $reason"
        }
    }

    private class OpenSession(val connectionId: Uuid, val name: String, val ssh: ShellSession.Ssh) {
        /** Forwards running on or starting with this session. Guarded by [lock]. */
        val users = mutableSetOf<Uuid>()
    }

    private class Running(val session: OpenSession, val handle: SshForwardHandle)

    private class ForwardFailure(override val message: String) : Exception(message)
}
