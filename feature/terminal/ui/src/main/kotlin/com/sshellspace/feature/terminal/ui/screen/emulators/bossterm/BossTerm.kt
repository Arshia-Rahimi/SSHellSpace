package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.getPlatformServices
import ai.rever.bossterm.compose.settings.TerminalSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * BossTerm's defaults assume its own full app. Here, kitty file transfers would let anything printed in the terminal,
 * including by a server, probe local files. AI assistant detection would intercept what the user types and offer to
 * install tools. The rest isn't started by the embedded terminal today; turning it off keeps it that way on upgrades.
 */
private val EmbeddedTerminalSettings = TerminalSettings(
    allowKittyFileTransfers = false,
    aiAssistantsEnabled = false,
    mcpEnabled = false,
    daemonEnabled = false,
    voiceCallEnabled = false,
    voiceCallShareEnabled = false,
)

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
    command: String,
    terminalState: EmbeddableTerminalState,
    initialCommand: String? = null,
    onExit: ((Int) -> Unit)? = null,
    platformServices: PlatformServices = getPlatformServices(),
) {
    ApplyTerminalTheme()

    EmbeddableTerminal(
        settings = EmbeddedTerminalSettings,
        onLinkClick = ::handleTerminalLink,
        command = command,
        initialCommand = initialCommand,
        onExit = onExit,
        state = terminalState,
        modifier = modifier,
        platformServices = platformServices,
    )
}
