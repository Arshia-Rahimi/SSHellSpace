package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshShellHandle
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal
import com.sshellspace.feature.terminal.ui.screen.emulators.TerminalFactory

internal object BossTermFactory : TerminalFactory {
    override fun local(shell: Shell): Terminal = BossTermTerminal(shell)

    override fun ssh(
        session: ShellSession.Ssh,
        shell: SshShellHandle,
        startupCommand: String?,
        onExit: (Int) -> Unit,
        onShellError: (String) -> Unit,
    ): Terminal = BossTermSshTerminal(session, shell, startupCommand, onExit, onShellError)
}
