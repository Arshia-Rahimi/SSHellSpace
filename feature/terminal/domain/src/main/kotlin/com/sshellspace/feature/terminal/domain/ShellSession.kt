package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.Shell

sealed interface ShellSession {
    data class Local(val shell: Shell) : ShellSession

    interface Ssh : ShellSession, AutoCloseable {
        val isAuthenticated: Boolean
        fun openShell(columns: Int = 80, rows: Int = 24): SshShellHandle
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
