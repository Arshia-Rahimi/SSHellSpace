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

    // Without Enter it goes in as a paste, so a multi-line command waits for review instead of running line by line.
    override fun sendText(text: String, pressEnter: Boolean) {
        if (pressEnter) embeddedTerminalState.write("$text\n") else embeddedTerminalState.paste(text)
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
