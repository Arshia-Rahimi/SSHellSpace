package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminalState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.ui.screen.emulators.Terminal

data class BossTermTerminal(
    val shell: Shell,
): Terminal {
    val embeddedTerminalState: EmbeddableTerminalState = EmbeddableTerminalState()

    override fun clear() {
        embeddedTerminalState.dispose()
    }

    @Composable
    override fun content(modifier: Modifier) {
        BossTerm(
            modifier = modifier,
            terminalState = embeddedTerminalState,
            command = shell.command ?: "",
        )
    }
}
