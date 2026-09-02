package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshConnectionStatus
import com.sshellspace.feature.terminal.domain.SshShellHandle
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession

class MinaSshConnectionService(
    private val sshClient: SshClient,
): SshConnectionService {
    private val _status = MutableStateFlow(SshConnectionStatus.Idle)
    override val status = _status.asStateFlow()

    override suspend fun connect() {
        TODO("Not yet implemented")
    }
}

private class MinaSshSession(private val session: ClientSession) : ShellSession.Ssh {
    override val isAuthenticated: Boolean
        get() = session.isAuthenticated

    override fun openShell(columns: Int, rows: Int): SshShellHandle {
        val channel = session.createShellChannel()
        channel.ptyType = "xterm-256color"
        channel.ptyColumns = columns
        channel.ptyLines = rows
        channel.open().verify(10L, TimeUnit.SECONDS)
        return MinaSshShellHandle(session, channel)
    }

    override fun close() {
        session.close(false)
    }
}

private class MinaSshShellHandle(
    private val session: ClientSession,
    private val channel: ChannelShell,
) : SshShellHandle {

    private val stdin = channel.invertedIn
    private val stdout = channel.invertedOut
    private val exitCodeRef = AtomicReference<Int?>(null)

    override suspend fun write(data: String) = writeBytes(data.toByteArray(Charsets.UTF_8))

    override suspend fun writeBytes(data: ByteArray) = withContext(Dispatchers.IO) {
        stdin.write(data); stdin.flush()
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val buf = ByteArray(8192)
        val n = stdout.read(buf)
        if (n < 0) null else String(buf, 0, n, Charsets.UTF_8)
    }

    override fun isAlive(): Boolean = !channel.isClosed && !channel.isClosing

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            channel.close(false); session.close(false)
        }
    }

    override suspend fun waitForExit(): Int = withContext(Dispatchers.IO) {
        channel.waitFor(setOf(ClientChannelEvent.CLOSED), 0L)
        (channel.exitStatus ?: -1).also { exitCodeRef.set(it) }
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        channel.sendWindowChange(columns, rows)
    }

    override fun exitCode(): Int? = exitCodeRef.get() ?: channel.exitStatus
}
