package com.sshellspace.feature.terminal.ui.screen.emulators.bossterm

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.EmbeddableTerminalState
import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.getPlatformServices
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.tabs.TerminalTab
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalWindowInfo
import com.sshellspace.core.domain.models.OS
import com.sshellspace.core.domain.models.getOs
import com.sshellspace.core.ui.tabs.LocalTabActive

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

/**
 * BossTerm lets Shift bypass mouse reporting so text can be selected in tmux or vim, but it reads Shift off the AWT
 * mouse event, which the Tao backend doesn't supply. Turning reporting off while Shift is held does the same.
 */
private val ShiftHeldSettings = EmbeddedTerminalSettings.copy(enableMouseReporting = false)

private val isMac = getOs() == OS.MacOS

private val ModifierKeys = setOf(
    Key.ShiftLeft, Key.ShiftRight,
    Key.CtrlLeft, Key.CtrlRight,
    Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight,
)

/**
 * The control character each Ctrl+key sends (Ctrl+A is 0x01, Ctrl+[ is Esc). On macOS, Tao hands BossTerm the typed
 * text rather than the control character, so Ctrl+C wouldn't interrupt. Ctrl isn't BossTerm's shortcut key there.
 */
private val ControlCodes: Map<Key, Byte> = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    Key.LeftBracket, Key.Backslash, Key.RightBracket,
).mapIndexed { index, key -> key to (index + 1).toByte() }.toMap()

// BossTerm keeps its selection internal.
private val sessionGetter = runCatching {
    EmbeddableTerminalState::class.java.getMethod("getSession\$ai_rever_bossterm_compose_ui")
}.getOrNull()

/** Whether text is selected, or null if this BossTerm version no longer lets it be read. */
private fun EmbeddableTerminalState.hasSelection(): Boolean? = runCatching {
    (sessionGetter?.invoke(this) as? TerminalTab)?.selectionTracker?.hasSelection()
}.getOrNull()

@Composable
fun BossTerm(
    modifier: Modifier = Modifier,
    command: String,
    terminalState: EmbeddableTerminalState,
    initialCommand: String? = null,
    onExit: ((Int) -> Unit)? = null,
    platformServices: PlatformServices = getPlatformServices(),
) {
    // BossTerm takes the keyboard when it first appears, so in a tab kept alive while hidden, such as an SSH session
    // that connects in the background, it waits until its tab is on show.
    val active = LocalTabActive.current
    var shown by remember { mutableStateOf(active) }
    if (active) shown = true
    if (!shown) return

    ApplyTerminalTheme()

    val shiftHeld = LocalWindowInfo.current.keyboardModifiers.isShiftPressed
    val terminalSettings by SettingsManager.instance.settings.collectAsState()

    Box(modifier) {
        EmbeddedBossTerm(shiftHeld, command, terminalState, initialCommand, onExit, platformServices)
        // Until the shell has started, BossTerm shows a gray "Connecting to shell..." loader. Starting one is
        // near-instant (an SSH tab opens its channel beforehand), so the loader would only flash. The terminal's own
        // background shows instead. Errors still show, since they aren't Initializing.
        if (terminalState.isInitializing) {
            Box(Modifier.fillMaxSize().background(terminalSettings.defaultBackgroundColor))
        }
    }
}

@Composable
private fun EmbeddedBossTerm(
    shiftHeld: Boolean,
    command: String,
    terminalState: EmbeddableTerminalState,
    initialCommand: String?,
    onExit: ((Int) -> Unit)?,
    platformServices: PlatformServices,
) {
    EmbeddableTerminal(
        settings = if (shiftHeld) ShiftHeldSettings else EmbeddedTerminalSettings,
        onLinkClick = ::handleTerminalLink,
        command = command,
        initialCommand = initialCommand,
        onExit = onExit,
        state = terminalState,
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val controlCode = ControlCodes[event.key]
                ?.takeIf { isMac && event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed }
            if (event.type == KeyEventType.KeyDown && controlCode != null) {
                terminalState.sendInput(byteArrayOf(controlCode))
                return@onPreviewKeyEvent true
            }
            event.type == KeyEventType.KeyDown && (
                // Tao reports a modifier's own key-down without its flag, so BossTerm takes it for typing and clears
                // the selection. A modifier alone never sends anything, so BossTerm loses nothing.
                event.key in ModifierKeys ||
                    // With nothing selected, BossTerm passes Cmd+C on as the letter c.
                    isMac && event.key == Key.C &&
                    event.isMetaPressed && !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed &&
                    terminalState.hasSelection() == false
                )
        },
        platformServices = platformServices,
    )
}
