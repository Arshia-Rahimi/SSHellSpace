package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.LocalWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.scrim
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textDisabled
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.printableRemoteText
import com.sshellspace.feature.terminal.ui.screen.ssh.Actions
import com.sshellspace.feature.terminal.ui.screen.ssh.Card
import com.sshellspace.feature.terminal.ui.screen.ssh.SshChallengeCard
import com.sshellspace.feature.terminal.ui.screen.ssh.SshHostKeyCard
import com.sshellspace.feature.terminal.ui.screen.ssh.SshSecretCard
import com.sshellspace.feature.terminal.ui.screen.ssh.StatusCard
import kotlinx.coroutines.launch

private val SizeColumnWidth = 90.dp
private val ModifiedColumnWidth = 140.dp
private val ModeColumnWidth = 100.dp
private val RowHeight = 30.dp
private val TransfersMaxHeight = 180.dp
private const val SELECTED_ALPHA = 0.18f
private const val DOUBLE_CLICK_MILLIS = 400L
private const val MAX_LISTED_NAMES = 5

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

@Composable
private fun Browser(uiState: SftpUiState, onAction: (SftpAction) -> Unit) {
    val state = uiState.browser
    val scope = rememberCoroutineScope()
    val download = { files: List<RemoteFile> ->
        scope.launch { pickFolder()?.let { onAction(SftpAction.Download(files, it)) } }
        Unit
    }

    var isDropHovering by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][background])
            .fileDropTarget(
                // A drop mustn't land behind an open question, such as whether to replace a file.
                enabled = state.dialog == null,
                onHoverChange = { isDropHovering = it },
                onDrop = { onAction(SftpAction.Upload(it)) },
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(state, onAction, onDownload = download)
            state.notice?.let { Notice(it) { onAction(SftpAction.DismissNotice) } }
            if (state.truncated) {
                Text(
                    "This folder is very large, so only part of it is listed. Use Go to path to reach other items.",
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            FileList(state, onAction, onDownload = download, modifier = Modifier.weight(1f))
            StatusBar(state)
            if (uiState.transfers.isNotEmpty()) TransfersPanel(uiState.transfers, onAction)
        }
        state.dialog?.let { Dialog(it, onAction) }
        if (isDropHovering && state.dialog == null) FileDropOverlay(state.path)
    }
}

@Composable
private fun Toolbar(state: SftpBrowserState, onAction: (SftpAction) -> Unit, onDownload: (List<RemoteFile>) -> Unit) {
    val selected = state.selectedFiles()
    Row(
        modifier = Modifier.fillMaxWidth().background(Theme[colors][surface]).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolbarButton(Icons.Up, "Up one folder", enabled = state.path != "/") { onAction(SftpAction.GoUp) }
        ToolbarButton(Icons.Home, "Home folder") { onAction(SftpAction.GoHome) }
        PathBar(state.path, onAction, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
        ToolbarButton(Icons.Refresh, "Refresh") { onAction(SftpAction.Refresh) }
        ToolbarButton(
            if (state.showHidden) Icons.EyeOpen else Icons.EyeClosed,
            if (state.showHidden) "Hide dotfiles" else "Show dotfiles",
        ) { onAction(SftpAction.ToggleHidden) }
        ToolbarButton(Icons.Plus, "New folder") { onAction(SftpAction.RequestNewFolder) }
        UploadMenu(onAction)
        ToolbarButton(Icons.Download, "Download", enabled = selected.isNotEmpty()) { onDownload(selected) }
        ToolbarButton(Icons.Pencil, "Rename", enabled = selected.size == 1) { onAction(SftpAction.RequestRename) }
        ToolbarButton(Icons.Trash, "Delete", enabled = selected.isNotEmpty()) { onAction(SftpAction.RequestDelete) }
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Tooltip(text = label, enabled = enabled) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Theme[colors][onSurface] else Theme[colors][textDisabled],
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(6.dp),
        )
    }
}

@Composable
private fun UploadMenu(onAction: (SftpAction) -> Unit) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        sideOffset = 4.dp,
        anchor = { ToolbarButton(Icons.Upload, "Upload") { expanded = !expanded } },
        panel = {
            MenuPanel {
                MenuItem(onClick = {
                    expanded = false
                    scope.launch { pickUploadFiles()?.let { onAction(SftpAction.Upload(it)) } }
                }) {
                    Text("Upload files…")
                }
                MenuItem(onClick = {
                    expanded = false
                    scope.launch { pickFolder()?.let { onAction(SftpAction.Upload(listOf(it))) } }
                }) {
                    Text("Upload a folder…")
                }
            }
        },
    )
}

/** Clickable path segments, or a text field for typing a path. */
@Composable
private fun PathBar(path: String, onAction: (SftpAction) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember(path) { mutableStateOf(false) }
    if (editing) {
        var text by remember { mutableStateOf(path) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Box(
            modifier = modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    editing = false
                    true
                } else {
                    false
                }
            },
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Path, such as ~/logs or /var/www",
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                focusRequester = focusRequester,
                onSubmit = {
                    editing = false
                    onAction(SftpAction.GoToPath(it))
                },
            )
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pathSegments(path).forEachIndexed { index, (label, target) ->
                    if (index > 1) Text("/", color = Theme[colors][textSecondary])
                    Button(
                        onClick = { onAction(SftpAction.Navigate(target)) },
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            ToolbarButton(Icons.Search, "Go to path") { editing = true }
        }
    }
}

/** Each folder on the way to [path], with where clicking it leads. */
internal fun pathSegments(path: String): List<Pair<String, String>> {
    if (!path.startsWith("/")) return emptyList()
    val names = path.split('/').filter { it.isNotEmpty() }
    return listOf("/" to "/") + names.indices.map { index ->
        printableRemoteText(names[index]) to names.take(index + 1).joinToString("/", prefix = "/")
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Theme[colors][surfaceElevated]).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = Theme[colors][error], modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, imageVector = Icons.Close, contentDescription = "Dismiss")
    }
}

@Composable
private fun FileList(
    state: SftpBrowserState,
    onAction: (SftpAction) -> Unit,
    onDownload: (List<RemoteFile>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember(state.entries, state.showHidden, state.sortColumn, state.sortAscending) { state.visibleEntries() }
    val showMode = !LocalWindowClass.current.isCompact
    val focusRequester = remember { FocusRequester() }
    val open = { file: RemoteFile ->
        when (file.kind) {
            RemoteFileKind.Directory -> onAction(SftpAction.Navigate(file.path))
            RemoteFileKind.File -> onDownload(listOf(file))
            RemoteFileKind.Other -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            // Not a preview handler: the dialogs' text fields need these keys, and get them first this way.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val command = event.isCtrlPressed || event.isMetaPressed
                val selected = state.selectedFiles()
                when {
                    command && event.key == Key.A -> onAction(SftpAction.SelectAll)
                    event.key == Key.Delete || (command && event.key == Key.Backspace) -> onAction(SftpAction.RequestDelete)
                    event.key == Key.Backspace -> onAction(SftpAction.GoUp)
                    event.key == Key.Enter && selected.size == 1 -> open(selected.single())
                    else -> return@onKeyEvent false
                }
                true
            },
    ) {
        HeaderRow(state, showMode, onAction)
        when {
            visible.isEmpty() && !state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.entries.isEmpty()) "This folder is empty. Drop files here to upload them." else "Only dotfiles here. Show them with the eye button.",
                    color = Theme[colors][textSecondary],
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.path }) { file ->
                    FileRow(
                        file = file,
                        selected = file.path in state.selection,
                        showMode = showMode,
                        onClick = { mode ->
                            focusRequester.requestFocus()
                            onAction(SftpAction.Click(file, mode))
                        },
                        onOpen = { open(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(state: SftpBrowserState, showMode: Boolean, onAction: (SftpAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderCell("Name", SftpSortColumn.Name, state, onAction, Modifier.weight(1f), TextAlign.Start)
        HeaderCell("Size", SftpSortColumn.Size, state, onAction, Modifier.width(SizeColumnWidth), TextAlign.End)
        HeaderCell("Modified", SftpSortColumn.Modified, state, onAction, Modifier.width(ModifiedColumnWidth), TextAlign.Start)
        if (showMode) Text("Permissions", color = Theme[colors][textSecondary], modifier = Modifier.width(ModeColumnWidth))
    }
}

@Composable
private fun HeaderCell(
    label: String,
    column: SftpSortColumn,
    state: SftpBrowserState,
    onAction: (SftpAction) -> Unit,
    modifier: Modifier,
    align: TextAlign,
) {
    val arrow = when {
        state.sortColumn != column -> ""
        state.sortAscending -> " ↑"
        else -> " ↓"
    }
    Button(onClick = { onAction(SftpAction.Sort(column)) }, modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(label + arrow, color = Theme[colors][textSecondary], textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: RemoteFile,
    selected: Boolean,
    showMode: Boolean,
    onClick: (SftpClickMode) -> Unit,
    onOpen: () -> Unit,
) {
    // Counted here because the built-in double click delays every single click, which makes selecting feel slow.
    var lastClick by remember { mutableStateOf(0L) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(if (selected) Theme[colors][primary].copy(alpha = SELECTED_ALPHA) else Color.Transparent)
            .onClick(keyboardModifiers = { isCtrlPressed || isMetaPressed }) { onClick(SftpClickMode.Toggle) }
            .onClick(keyboardModifiers = { isShiftPressed }) { onClick(SftpClickMode.Range) }
            // Matches only without modifier keys. Matching any would take the event from the two handlers above.
            .onClick(keyboardModifiers = { !isCtrlPressed && !isMetaPressed && !isShiftPressed }) {
                val now = System.currentTimeMillis()
                onClick(SftpClickMode.Single)
                if (now - lastClick < DOUBLE_CLICK_MILLIS) {
                    lastClick = 0L
                    onOpen()
                } else {
                    lastClick = now
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnstyledIcon(
                imageVector = when {
                    file.isSymlink -> Icons.Link
                    file.isDirectory -> Icons.Folder
                    else -> Icons.File
                },
                contentDescription = when {
                    file.isSymlink -> "Link"
                    file.isDirectory -> "Folder"
                    else -> "File"
                },
                tint = if (file.isDirectory) Theme[colors][primary] else Theme[colors][textSecondary],
            )
            Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (file.kind == RemoteFileKind.File) formatSize(file.size) else "",
            color = Theme[colors][textSecondary],
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(SizeColumnWidth),
        )
        Text(
            formatModified(file.modifiedMillis),
            color = Theme[colors][textSecondary],
            maxLines = 1,
            modifier = Modifier.width(ModifiedColumnWidth),
        )
        if (showMode) {
            Text(
                formatMode(file.permissions),
                color = Theme[colors][textSecondary],
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.width(ModeColumnWidth),
            )
        }
    }
}

@Composable
private fun StatusBar(state: SftpBrowserState) {
    val visible = state.visibleEntries()
    val text = state.busyMessage ?: when {
        state.isLoading -> "Loading…"
        state.selection.isNotEmpty() -> {
            val selected = state.selectedFiles()
            "${selected.size} of ${visible.size} selected, ${formatSize(selected.filter { it.kind == RemoteFileKind.File }.sumOf { it.size })}"
        }
        else -> if (visible.size == 1) "1 item" else "${visible.size} items"
    }
    Text(
        text,
        color = Theme[colors][textSecondary],
        modifier = Modifier.fillMaxWidth().background(Theme[colors][surface]).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun TransfersPanel(transfers: List<SftpTransfer>, onAction: (SftpAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Theme[colors][surface])) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Transfers", modifier = Modifier.weight(1f))
            if (transfers.any { it.status.isFinished }) {
                Button(onClick = { onAction(SftpAction.ClearFinishedTransfers) }) {
                    Text("Clear finished", color = Theme[colors][primary])
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = TransfersMaxHeight)) {
            items(transfers, key = { it.id }) { transfer ->
                TransferRow(transfer) { onAction(SftpAction.CancelTransfer(transfer.id)) }
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: SftpTransfer, onCancel: () -> Unit) {
    val status = transfer.status
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnstyledIcon(
            imageVector = if (transfer.direction == SftpDirection.Download) Icons.Download else Icons.Upload,
            contentDescription = transfer.direction.name,
            tint = Theme[colors][textSecondary],
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(transfer.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status == SftpTransferStatus.Running) ProgressBar(transfer.fraction())
            Text(
                transfer.statusText(),
                color = if (status is SftpTransferStatus.Failed) Theme[colors][error] else Theme[colors][textSecondary],
            )
        }
        if (!status.isFinished) {
            IconButton(onClick = onCancel, imageVector = Icons.Close, contentDescription = "Cancel ${transfer.name}")
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Theme[colors][surfaceElevated]),
    ) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Theme[colors][primary]))
    }
}

internal fun SftpTransfer.fraction(): Float {
    val done = progress ?: return 0f
    if (done.bytesTotal <= 0) return 0f
    return (done.bytesDone.toFloat() / done.bytesTotal).coerceIn(0f, 1f)
}

internal fun SftpTransfer.statusText(): String = when (val current = status) {
    SftpTransferStatus.Queued -> "Waiting"
    SftpTransferStatus.Running -> progress?.let { done ->
        val files = if (done.filesTotal > 1) ", ${done.filesDone} of ${done.filesTotal} files" else ""
        "${formatSize(done.bytesDone)} of ${formatSize(done.bytesTotal)}$files"
    } ?: "Preparing"
    SftpTransferStatus.Done -> buildString {
        append("Done")
        progress?.let { append(", ").append(formatSize(it.bytesDone)) }
        if (skipped.isNotEmpty()) {
            append(". Skipped ").append(skipped.size).append(": ")
            append(skipped.take(MAX_LISTED_NAMES).joinToString("; "))
            if (skipped.size > MAX_LISTED_NAMES) append("; …")
        }
    }
    SftpTransferStatus.Cancelled -> "Cancelled"
    is SftpTransferStatus.Failed -> current.message
}

@Composable
private fun Dialog(dialog: SftpDialog, onAction: (SftpAction) -> Unit) {
    val dismiss = { onAction(SftpAction.DismissDialog) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][scrim])
            // Keeps clicks away from the list behind.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        when (dialog) {
            is SftpDialog.NewFolder -> NameDialog(
                title = "New folder",
                initial = "",
                confirmLabel = "Create",
                errorMessage = dialog.error,
                onConfirm = { onAction(SftpAction.CreateFolder(it)) },
                onDismiss = dismiss,
            )
            is SftpDialog.Rename -> NameDialog(
                title = "Rename ${dialog.file.displayName}",
                initial = dialog.file.name,
                confirmLabel = "Rename",
                errorMessage = dialog.error,
                onConfirm = { onAction(SftpAction.Rename(dialog.file, it)) },
                onDismiss = dismiss,
            )
            is SftpDialog.ConfirmDelete -> Card {
                val only = dialog.files.singleOrNull()
                Text(if (only != null) "Delete ${only.displayName}?" else "Delete ${dialog.files.size} items?", color = Theme[colors][error])
                Text(deleteMessage(dialog.files), color = Theme[colors][textSecondary])
                Actions(
                    primaryAction = "Delete" to { onAction(SftpAction.Delete(dialog.files)) },
                    secondaryAction = "Cancel" to dismiss,
                    destructive = true,
                )
            }
            is SftpDialog.Conflict -> Card {
                Text(if (dialog.names.size == 1) "${dialog.names.single()} already exists" else "${dialog.names.size} items already exist")
                Text(
                    listOfNotNull(
                        dialog.names.takeIf { it.size > 1 }?.take(MAX_LISTED_NAMES)?.joinToString(", "),
                        "Replacing overwrites files with the same name and merges folders. It can't be undone.",
                    ).joinToString("\n"),
                    color = Theme[colors][textSecondary],
                )
                Actions(
                    primaryAction = "Replace" to { onAction(SftpAction.ResolveConflict(SftpConflictChoice.Replace)) },
                    secondaryAction = "Cancel" to dismiss,
                    middleAction = "Keep both" to { onAction(SftpAction.ResolveConflict(SftpConflictChoice.KeepBoth)) },
                    destructive = true,
                )
            }
        }
    }
}

private fun deleteMessage(files: List<RemoteFile>): String = buildString {
    if (files.size > 1) append(files.take(MAX_LISTED_NAMES).joinToString(", ") { it.displayName }).append(". ")
    if (files.any { it.isDirectory && !it.isSymlink }) append("Folders are deleted with everything in them. ")
    if (files.any { it.isSymlink }) append("Links are removed, not what they point to. ")
    append("This can't be undone.")
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Card {
        Text(title)
        Box(
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        ) {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = "Name",
                isError = errorMessage != null,
                focusRequester = focusRequester,
                onSubmit = onConfirm,
            )
        }
        errorMessage?.let { Text(it, color = Theme[colors][error]) }
        Actions(
            primaryAction = confirmLabel to { onConfirm(value) },
            secondaryAction = "Cancel" to onDismiss,
        )
    }
}
