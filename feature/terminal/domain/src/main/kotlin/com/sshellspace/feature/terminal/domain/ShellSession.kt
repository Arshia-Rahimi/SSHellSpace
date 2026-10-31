package com.sshellspace.feature.terminal.domain

import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface ShellSession {
    data class Local(val shell: Shell) : ShellSession

    interface Ssh : ShellSession, AutoCloseable {
        val isAuthenticated: Boolean
        fun openShell(columns: Int = 80, rows: Int = 24): SshShellHandle

        /** Starts [forward] on the target host. Blocks until the listener is bound. Close the handle to stop it. */
        fun startForward(forward: PortForward): SshForwardHandle

        /** Calls [listener] once when the connection closes, from either side. */
        fun onClose(listener: () -> Unit)
    }
}

interface SshShellHandle {
    suspend fun write(data: String)
    suspend fun writeBytes(data: ByteArray)
    suspend fun read(): String?
    fun isAlive(): Boolean
    suspend fun resize(columns: Int, rows: Int)
    suspend fun close()
    suspend fun waitForExit(): Int
    fun exitCode(): Int?
}

interface SshForwardHandle : AutoCloseable {
    /** The address that is listening: on this device for local and dynamic forwards, on the server for remote. */
    val boundAddress: String
}
