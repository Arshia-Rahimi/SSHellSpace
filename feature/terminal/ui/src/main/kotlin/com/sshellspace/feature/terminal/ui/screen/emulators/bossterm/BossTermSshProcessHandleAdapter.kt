package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.PlatformServices
import com.sshellspace.feature.terminal.domain.SshShellHandle

class BossTermSshProcessHandleAdapter(
    private val shell: SshShellHandle,
) : PlatformServices.ProcessService.ProcessHandle {
    override suspend fun write(data: String) = shell.write(data)
    override suspend fun writeBytes(data: ByteArray) = shell.writeBytes(data)
    override suspend fun read(): String? = shell.read()
    override fun isAlive(): Boolean = shell.isAlive()
    override suspend fun kill() = shell.close()
    override suspend fun waitFor(): Int = shell.waitForExit()
    override suspend fun resize(columns: Int, rows: Int) = shell.resize(columns, rows)
    override fun getExitCode(): Int? = shell.exitCode()
    override fun getPid(): Long? = null
    override fun getWorkingDirectory(): String? = null
}
