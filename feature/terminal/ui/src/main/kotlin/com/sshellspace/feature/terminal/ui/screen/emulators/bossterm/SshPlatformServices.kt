package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.getPlatformServices
import com.sshellspace.feature.terminal.domain.ShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BossTerm's platform services, except that "spawning a process" opens a shell channel on [session]
 * instead of a local PTY. BossTerm then drives the remote shell exactly like a local one.
 */
internal class SshPlatformServices(
    private val session: ShellSession.Ssh,
    private val onShellError: (String) -> Unit,
    delegate: PlatformServices = getPlatformServices(),
) : PlatformServices by delegate {

    private val processService = object : PlatformServices.ProcessService {
        override suspend fun spawnProcess(
            config: PlatformServices.ProcessService.ProcessConfig,
        ): PlatformServices.ProcessService.ProcessHandle? = withContext(Dispatchers.IO) {
            try {
                // BossTerm resizes the channel to the real grid right after this returns.
                BossTermSshProcessHandleAdapter(session.openShell())
            } catch (e: Exception) {
                onShellError(e.message ?: "Couldn't open a shell")
                null
            }
        }
    }

    override fun getProcessService(): PlatformServices.ProcessService = processService
}
