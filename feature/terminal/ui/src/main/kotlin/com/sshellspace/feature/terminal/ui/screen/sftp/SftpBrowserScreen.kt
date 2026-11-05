package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.warning
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.ui.screen.ssh.SshChallengeCard
import com.sshellspace.feature.terminal.ui.screen.ssh.SshHostKeyCard
import com.sshellspace.feature.terminal.ui.screen.ssh.SshSecretCard
import com.sshellspace.feature.terminal.ui.screen.ssh.StatusCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BANNER_TINT_ALPHA = 0.12f
private const val LOADING_BAR_DELAY_MILLIS = 150L
private const val LOADING_BAR_CYCLE_MILLIS = 1100
private const val LOADING_BAR_WIDTH = 0.3f
private const val MENU_CLOSING_FRAMES = 3

@Composable
fun SftpBrowserScreen(viewModel: SftpBrowserViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::execute

    when (val current = uiState.connection) {
        SftpConnectionState.Connected -> Browser(uiState, onAction)
        is SftpConnectionState.Connecting -> StatusCard(
            title = "Connecting to ${current.title}…",
            primaryAction = null,
            secondaryAction = "Cancel" to { onAction(SftpAction.CancelConnecting) },
        )
        is SftpConnectionState.AwaitingHostKey -> SshHostKeyCard(current.prompt) {
            onAction(SftpAction.AnswerHostKey(trust = it))
        }
        is SftpConnectionState.AwaitingSecret -> SshSecretCard(
            prompt = current.prompt,
            onSubmit = { onAction(SftpAction.SubmitSecret(it)) },
            onCancel = { onAction(SftpAction.CancelConnecting) },
        )
        is SftpConnectionState.AwaitingChallenge -> SshChallengeCard(
            challenge = current.challenge,
            onSubmit = { onAction(SftpAction.SubmitChallenge(it)) },
            onCancel = { onAction(SftpAction.CancelConnecting) },
        )
        is SftpConnectionState.Failed -> StatusCard(
            title = "Couldn't connect",
            message = current.message,
            isError = true,
            primaryAction = "Try again" to { onAction(SftpAction.Reconnect) },
        )
        SftpConnectionState.Cancelled -> StatusCard(
            title = "Connection cancelled",
            primaryAction = "Connect" to { onAction(SftpAction.Reconnect) },
        )
        is SftpConnectionState.Disconnected -> StatusCard(
            title = "Connection to ${current.title} closed",
            message = "Transfers that were running have stopped.",
            primaryAction = "Reconnect" to { onAction(SftpAction.Reconnect) },
        )
    }
}

/** Where the keyboard is on this screen. It belongs to the list unless something is being typed or asked. */
@Stable
internal class SftpFocus {
    val list = FocusRequester()
    val filter = FocusRequester()
    var editingPath by mutableStateOf(false)
    var filterFocused by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)

    val isTyping: Boolean get() = editingPath || filterFocused
}

/** What the toolbar and the context menu both offer that starts with a file dialog. */
internal class SftpPickers(
    val download: (List<RemoteFile>) -> Unit,
    val uploadFiles: () -> Unit,
    val uploadFolder: () -> Unit,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Browser(uiState: SftpUiState, onAction: (SftpAction) -> Unit) {
    val state = uiState.browser
    val scope = rememberCoroutineScope()
    val pickers = remember(onAction) {
        SftpPickers(
            download = { files -> scope.launch { pickFolder()?.let { onAction(SftpAction.Download(files, it)) } } },
            uploadFiles = { scope.launch { pickUploadFiles()?.let { onAction(SftpAction.Upload(it)) } } },
            uploadFolder = { scope.launch { pickFolder()?.let { onAction(SftpAction.Upload(listOf(it))) } } },
        )
    }
    // Transfers update this screen many times a second, and sorting a large folder that often would show.
    val visible = remember(state.entries, state.showHidden, state.filter, state.sortColumn, state.sortAscending) {
        state.visibleEntries()
    }
    val selected = remember(visible, state.selection) { visible.filter { it.path in state.selection } }

    var isDropHovering by remember { mutableStateOf(false) }
    val focus = remember { SftpFocus() }
    val dialogOpen by rememberUpdatedState(state.dialog != null)

    LaunchedEffect(state.path) { focus.editingPath = false }
    // Also covers the first listing. A menu is a layer of its own, and focus only gets out once that has gone.
    LaunchedEffect(state.dialog == null, focus.menuOpen) {
        if (state.dialog == null && !focus.menuOpen) {
            repeat(MENU_CLOSING_FRAMES) { withFrameNanos { } }
            focus.list.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][background])
            // A clicked button keeps the keyboard, which would leave the list's keys dead and the button lit up.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val released = event.type == PointerEventType.Release && event.button == PointerButton.Primary
                        if (released && !dialogOpen && !focus.isTyping) focus.list.requestFocus()
                    }
                }
            }
            .onKeyEvent { event ->
                val command = browserCommand(event).takeIf { state.dialog == null } ?: return@onKeyEvent false
                when (command) {
                    BrowserCommand.Filter -> focus.filter.requestFocus()
                    BrowserCommand.TypePath -> focus.editingPath = true
                    BrowserCommand.Refresh -> onAction(SftpAction.Refresh)
                    BrowserCommand.NewFolder -> onAction(SftpAction.RequestNewFolder)
                }
                true
            }
            .fileDropTarget(
                // A drop mustn't land behind an open question, such as whether to replace a file.
                enabled = state.dialog == null,
                onHoverChange = { isDropHovering = it },
                onDrop = { onAction(SftpAction.Upload(it)) },
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box {
                Toolbar(state, selected, focus, pickers, onAction)
                if (state.isLoading || state.busyMessage != null) {
                    LoadingBar(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            state.notice?.let { Banner(it, Theme[colors][error]) { onAction(SftpAction.DismissNotice) } }
            if (state.truncated) {
                Banner(
                    message = "This folder is very large, so only part of it is listed. Type a path in the path bar to reach other items.",
                    tint = Theme[colors][warning],
                )
            }
            FileList(
                state = state,
                visible = visible,
                selected = selected,
                focus = focus,
                pickers = pickers,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            if (uiState.transfers.isNotEmpty()) TransfersPanel(uiState.transfers, state.transfersCollapsed, onAction)
            StatusBar(state, visible, selected)
        }
        state.dialog?.let { Dialog(it, state, visible, pickers, onAction) }
        if (isDropHovering && state.dialog == null) FileDropOverlay(state.path, state.isThisDevice)
    }
}

private enum class BrowserCommand { Filter, TypePath, Refresh, NewFolder }

/** Keys that work wherever the keyboard is on this screen, unlike the list's own. */
private fun browserCommand(event: KeyEvent): BrowserCommand? {
    if (event.type != KeyEventType.KeyDown) return null
    val command = event.isCtrlPressed || event.isMetaPressed
    return when {
        event.key == Key.F5 -> BrowserCommand.Refresh
        !command -> null
        event.isShiftPressed -> BrowserCommand.NewFolder.takeIf { event.key == Key.N }
        event.key == Key.F -> BrowserCommand.Filter
        event.key == Key.L -> BrowserCommand.TypePath
        event.key == Key.R -> BrowserCommand.Refresh
        else -> null
    }
}

/** Waits a moment before showing, so a listing that comes back straight away doesn't flash it. */
@Composable
internal fun LoadingBar(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_BAR_DELAY_MILLIS)
        show = true
    }
    if (!show) return
    val position by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOADING_BAR_CYCLE_MILLIS, easing = LinearEasing)),
    )
    val color = Theme[colors][primary]
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        val width = size.width * LOADING_BAR_WIDTH
        drawRect(
            color = color,
            topLeft = Offset((size.width + width) * position - width, 0f),
            size = Size(width, size.height),
        )
    }
}

@Composable
private fun Banner(message: String, tint: Color, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = BANNER_TINT_ALPHA))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnstyledIcon(imageVector = Icons.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(message, color = Theme[colors][textPrimary], fontSize = 13.sp, modifier = Modifier.weight(1f))
        onDismiss?.let {
            HoverButton(onClick = it, modifier = Modifier.size(20.dp)) {
                UnstyledIcon(
                    imageVector = Icons.Close,
                    contentDescription = "Dismiss",
                    tint = Theme[colors][onSurface],
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBar(state: SftpBrowserState, visible: List<RemoteFile>, selected: List<RemoteFile>) {
    Column(modifier = Modifier.fillMaxWidth().background(Theme[colors][surface])) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                state.summary(visible, selected),
                color = Theme[colors][textSecondary],
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            (state.busyMessage ?: "Loading…".takeIf { state.isLoading })?.let {
                Text(it, color = Theme[colors][textSecondary], fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
