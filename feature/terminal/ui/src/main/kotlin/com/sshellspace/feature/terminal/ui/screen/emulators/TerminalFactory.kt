package com.sshellspace.feature.terminal.ui.screen.emulators

import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshShellHandle
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.TerminalEmulator
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.BossTermFactory

/** Makes the terminals of one [TerminalEmulator], so the tabs don't depend on which one the user picked. */
interface TerminalFactory {
    /** A terminal running [shell] on this computer. */
    fun local(shell: Shell): Terminal

    /**
     * A terminal showing [shell], opened on an authenticated SSH [session]. Clearing it closes the session.
     * [startupCommand] is typed once the shell is ready. [onExit] gets the shell's exit code, or
     * [com.sshellspace.feature.terminal.domain.SESSION_LOST_EXIT_CODE] if the connection dropped, and
     * [onShellError] is called if another shell channel can't be opened.
     */
    fun ssh(
        session: ShellSession.Ssh,
        shell: SshShellHandle,
        startupCommand: String?,
        onExit: (Int) -> Unit,
        onShellError: (String) -> Unit,
    ): Terminal
}

val TerminalEmulator.factory: TerminalFactory
    get() = when (this) {
        TerminalEmulator.BossTerm -> BossTermFactory
    }

val TerminalEmulator.label: String
    get() = when (this) {
        TerminalEmulator.BossTerm -> "BossTerm"
    }
