package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshShellHandle
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal
import com.sshellspace.feature.terminal.ui.screen.emulators.TerminalFactory

/** A terminal drawn by JediTerm's emulator. Clearing it ends the program it shows. */
internal class JediTermTerminal(private val session: JediTermSession) : Terminal {
    override fun clear() = session.close()

    // Without Enter it goes in as a paste, so a multi-line command waits for review instead of running line by line.
    override fun sendText(text: String, pressEnter: Boolean) {
        if (pressEnter) session.type(text.replace("\r\n", "\r").replace('\n', '\r') + "\r") else session.paste(text)
    }

    @Composable
    override fun content(modifier: Modifier) = JediTermView(session, modifier)
}

internal object JediTermFactory : TerminalFactory {
    override fun local(shell: Shell): Terminal = JediTermTerminal(JediTermSession(PtyShellProcess(shell)))

    override fun ssh(
        session: ShellSession.Ssh,
        shell: SshShellHandle,
        startupCommand: String?,
        onExit: (Int) -> Unit,
        onShellError: (String) -> Unit,
    ): Terminal = SshJediTermTerminal(session, JediTermSession(SshShellProcess(shell), startupCommand, onExit))
}

/** Clearing it also closes the SSH session the shell runs on. */
private class SshJediTermTerminal(
    private val ssh: ShellSession.Ssh,
    private val session: JediTermSession,
) : Terminal by JediTermTerminal(session) {
    override fun clear() {
        session.close()
        ssh.close()
    }
}
