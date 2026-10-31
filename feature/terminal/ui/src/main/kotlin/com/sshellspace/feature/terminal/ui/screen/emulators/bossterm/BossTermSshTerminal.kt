package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminalState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal

/** A BossTerm terminal attached to an authenticated SSH session. Clearing it closes the session. */
class BossTermSshTerminal(
    private val session: ShellSession.Ssh,
    private val startupCommand: String?,
    private val onExit: (Int) -> Unit,
    onShellError: (String) -> Unit,
) : Terminal {
    private val embeddedTerminalState = EmbeddableTerminalState()
    private val platformServices = SshPlatformServices(session, onShellError)

    override fun clear() {
        embeddedTerminalState.dispose()
        session.close()
    }

    @Composable
    override fun content(modifier: Modifier) {
        BossTerm(
            modifier = modifier,
            // Only used for BossTerm's bookkeeping; the shell itself comes from the SSH session.
            command = "ssh",
            terminalState = embeddedTerminalState,
            initialCommand = startupCommand,
            onExit = onExit,
            platformServices = platformServices,
        )
    }
}
