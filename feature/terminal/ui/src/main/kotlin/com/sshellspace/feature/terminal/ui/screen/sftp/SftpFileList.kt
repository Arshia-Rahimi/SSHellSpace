package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import com.composeunstyled.LocalTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.LocalWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.hoverOverlay
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.printableRemoteText
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SizeColumnWidth = 80.dp
private val ModifiedColumnWidth = 112.dp
private val ModeColumnWidth = 84.dp
private val ColumnGap = 12.dp
private val RowInset = 4.dp
private val RowPadding = 8.dp
private val HeaderCellPadding = 4.dp
private val RowHeight = 28.dp
private val RowShape = RoundedCornerShape(4.dp)
private val ContextMenuWidth = 220.dp
private val ContextItemPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
private val NameFontSize = 13.sp
private val DetailFontSize = 12.sp
private const val SELECTED_ALPHA = 0.18f
private const val SELECTED_HOVER_ALPHA = 0.26f
private const val SCROLLBAR_ALPHA = 0.3f
private const val SCROLLBAR_HOVER_ALPHA = 0.6f
private const val SCROLLBAR_HOVER_MILLIS = 150
private const val DOUBLE_CLICK_MILLIS = 400L
private const val CONTEXT_ROWS_ABOVE = 3
private const val ANY_EXECUTE_BIT = 0b001_001_001

private val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "zst", "7z", "rar", "jar", "deb", "rpm", "iso")
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "ico", "tif", "tiff", "heic")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac")
private val TEXT_EXTENSIONS = setOf("txt", "md", "log", "csv", "rst", "pdf", "doc", "docx")
private val CODE_EXTENSIONS = setOf(
    "c", "cpp", "css", "go", "h", "html", "java", "js", "json", "jsx", "kt", "kts", "php", "py", "rb", "rs", "sh",
    "bash", "zsh", "sql", "toml", "ts", "tsx", "xml", "yaml", "yml", "ini", "conf", "cfg", "env", "properties",
)

/**
 * An icon for telling rows apart at a glance. It only goes by the name and mode a server chose, so nothing may rely on
 * it to say what a file really is.
 */
internal fun fileIcon(file: RemoteFile): ImageVector {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    val isExecutable = file.kind == RemoteFileKind.File && (file.permissions ?: 0) and ANY_EXECUTE_BIT != 0
    return when {
        file.isSymlink -> Icons.Link
        file.isDirectory -> Icons.Folder
        extension in ARCHIVE_EXTENSIONS -> Icons.Archive
        extension in IMAGE_EXTENSIONS -> Icons.Image
        extension in VIDEO_EXTENSIONS -> Icons.Video
        extension in AUDIO_EXTENSIONS -> Icons.Audio
        extension in TEXT_EXTENSIONS -> Icons.FileText
        extension in CODE_EXTENSIONS || isExecutable -> Icons.Code
        else -> Icons.File
    }
}

/**
 * Where the context menu is, and whether it's for the selected rows or for the folder. It stays set once closed: the
 * menu takes a few frames to go, and shouldn't jump to the corner or change its items meanwhile.
 */
private data class ContextMenuState(val position: IntOffset = IntOffset.Zero, val onRow: Boolean = false, val isOpen: Boolean = false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileList(
    state: SftpBrowserState,
    visible: List<RemoteFile>,
    selected: List<RemoteFile>,
    focus: SftpFocus,
    pickers: SftpPickers,
    onAction: (SftpAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showMode = !LocalWindowClass.current.isCompact
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(ContextMenuState()) }
    val open = { file: RemoteFile ->
        when (file.kind) {
            RemoteFileKind.Directory -> onAction(SftpAction.Navigate(file.path))
            RemoteFileKind.File -> if (file.canPreview) onAction(SftpAction.OpenPreview(file)) else pickers.download(listOf(file))
            RemoteFileKind.Other -> Unit
        }
    }

    var shownPath by remember { mutableStateOf(state.path) }
    LaunchedEffect(state.path, state.selectionCursor) {
        val cursor = visible.indexOfFirst { it.path == state.selectionCursor }
        when {
            // Otherwise a new folder would open as far down as the last one was scrolled.
            state.path != shownPath -> {
                shownPath = state.path
                listState.scrollToItem((cursor - CONTEXT_ROWS_ABOVE).coerceAtLeast(0))
            }
            state.revealCursor && cursor >= 0 -> listState.reveal(cursor)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus.list)
            .focusable()
            // Not a preview handler: the dialogs' text fields need these keys, and get them first this way.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || state.dialog != null) return@onKeyEvent false
                val command = event.isCtrlPressed || event.isMetaPressed
                val opening = event.key == Key.Enter || event.key == Key.NumPadEnter || (command && event.key == Key.DirectionDown)
                when {
                    opening && selected.size == 1 -> open(selected.single())
                    event.key == Key.Spacebar && selected.singleOrNull()?.canPreview == true ->
                        onAction(SftpAction.OpenPreview(selected.single()))
                    event.key == Key.Escape && selected.isNotEmpty() -> onAction(SftpAction.ClearSelection)
                    event.key == Key.Escape && state.filter.isNotEmpty() -> onAction(SftpAction.SetFilter(""))
                    else -> onAction(listAction(event, visible.size, listState.layoutInfo.visibleItemsInfo.size) ?: return@onKeyEvent false)
                }
                true
            },
    ) {
        HeaderRow(state, showMode, onAction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSecondaryPress { position ->
                    val row = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { position.y.roundToInt() in it.offset until it.offset + it.size }
                    val file = visible.firstOrNull { it.path == row?.key }
                    // Like a desktop file manager: a row outside the selection replaces it, one inside acts on all of it.
                    if (file != null && file.path !in state.selection) onAction(SftpAction.Click(file, SftpClickMode.Single))
                    menu = ContextMenuState(position.round(), onRow = file != null, isOpen = true)
                    focus.menuOpen = true
                }
                .onClick(keyboardModifiers = { !isCtrlPressed && !isMetaPressed && !isShiftPressed }) {
                    focus.list.requestFocus()
                    onAction(SftpAction.ClearSelection)
                },
        ) {
            if (visible.isEmpty() && !state.isLoading) {
                EmptyFolder(state, onAction)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.path }) { file ->
                        FileRow(
                            file = file,
                            selected = file.path in state.selection,
                            showMode = showMode,
                            onClick = { mode ->
                                focus.list.requestFocus()
                                onAction(SftpAction.Click(file, mode))
                            },
                            onOpen = { open(file) },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    style = sftpScrollbarStyle(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(1.dp),
                )
            }
            ContextMenu(
                menu = menu,
                state = state,
                selected = selected,
                pickers = pickers,
                onOpen = open,
                onAction = onAction,
                onDismiss = {
                    menu = menu.copy(isOpen = false)
                    focus.menuOpen = false
                },
            )
        }
    }
}

/** What a key does in the list that only takes an [SftpAction] to carry out, given how many rows there are and fit. */
private fun listAction(event: KeyEvent, rowCount: Int, rowsOnScreen: Int): SftpAction? {
    val command = event.isCtrlPressed || event.isMetaPressed
    val extend = event.isShiftPressed
    return when {
        command && event.key == Key.A -> SftpAction.SelectAll
        event.key == Key.Delete || (command && event.key == Key.Backspace) -> SftpAction.RequestDelete
        event.key == Key.Backspace || (command && event.key == Key.DirectionUp) -> SftpAction.GoUp
        event.key == Key.F2 -> SftpAction.RequestRename
        command -> null
        event.key == Key.DirectionUp -> SftpAction.MoveSelection(-1, extend)
        event.key == Key.DirectionDown -> SftpAction.MoveSelection(1, extend)
        event.key == Key.PageUp -> SftpAction.MoveSelection(-rowsOnScreen, extend)
        event.key == Key.PageDown -> SftpAction.MoveSelection(rowsOnScreen, extend)
        event.key == Key.MoveHome -> SftpAction.MoveSelection(-rowCount, extend)
        event.key == Key.MoveEnd -> SftpAction.MoveSelection(rowCount, extend)
        else -> null
    }
}

/** Scrolls no further than it takes to show the whole row, so the list doesn't jump around under the keyboard. */
private suspend fun LazyListState.reveal(index: Int) {
    val info = layoutInfo
    val whole = info.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset }
    when {
        whole.any { it.index == index } -> Unit
        index < (whole.firstOrNull()?.index ?: 0) -> scrollToItem(index)
        else -> scrollToItem((index - whole.size + 1).coerceAtLeast(0))
    }
}

@Composable
private fun Modifier.onSecondaryPress(onPress: (Offset) -> Unit): Modifier {
    val pressed by rememberUpdatedState(onPress)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    pressed(event.changes.first().position)
                }
            }
        }
    }
}

@Composable
internal fun sftpScrollbarStyle() = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = 6.dp,
    shape = RoundedCornerShape(3.dp),
    hoverDurationMillis = SCROLLBAR_HOVER_MILLIS,
    unhoverColor = Theme[colors][textSecondary].copy(alpha = SCROLLBAR_ALPHA),
    hoverColor = Theme[colors][textSecondary].copy(alpha = SCROLLBAR_HOVER_ALPHA),
)

@Composable
private fun EmptyFolder(state: SftpBrowserState, onAction: (SftpAction) -> Unit) {
    when {
        state.filter.isNotEmpty() -> EmptyState(
            icon = Icons.Search,
            title = "Nothing here matches “${state.filter}”",
            actionLabel = "Clear filter",
            onAction = { onAction(SftpAction.SetFilter("")) },
        )
        state.entries.isEmpty() -> EmptyState(
            icon = Icons.Folder,
            title = "This folder is empty",
            message = "Drop files here to ${if (state.isThisDevice) "copy" else "upload"} them.",
            actionLabel = "New folder",
            onAction = { onAction(SftpAction.RequestNewFolder) },
        )
        else -> EmptyState(
            icon = Icons.EyeClosed,
            title = "Only dotfiles here",
            actionLabel = "Show dotfiles",
            onAction = { onAction(SftpAction.ToggleHidden) },
        )
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        // Scrolls because a short window with transfers on show leaves little room.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UnstyledIcon(imageVector = icon, contentDescription = null, tint = Theme[colors][textSecondary], modifier = Modifier.size(28.dp))
        Text(title, textAlign = TextAlign.Center)
        message?.let { Text(it, color = Theme[colors][textSecondary], fontSize = NameFontSize, textAlign = TextAlign.Center) }
        Button(
            onClick = onAction,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(shape)
                .border(1.dp, Theme[colors][border], shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(actionLabel, fontSize = NameFontSize)
        }
    }
}

@Composable
private fun ContextMenu(
    menu: ContextMenuState,
    state: SftpBrowserState,
    selected: List<RemoteFile>,
    pickers: SftpPickers,
    onOpen: (RemoteFile) -> Unit,
    onAction: (SftpAction) -> Unit,
    onDismiss: () -> Unit,
) {
    UnstyledDropdownMenu(
        expanded = menu.isOpen,
        onExpandedChange = { if (!it) onDismiss() },
        modifier = Modifier.offset { menu.position },
        anchor = { Box(modifier = Modifier.size(1.dp)) },
        panel = {
            MenuPanel(width = ContextMenuWidth, onDismiss = onDismiss) {
                if (menu.onRow) {
                    RowMenu(state, selected, pickers, onOpen, onAction)
                } else {
                    FolderMenu(state, pickers, onAction)
                }
            }
        },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowMenu(
    state: SftpBrowserState,
    selected: List<RemoteFile>,
    pickers: SftpPickers,
    onOpen: (RemoteFile) -> Unit,
    onAction: (SftpAction) -> Unit,
) {
    val only = selected.singleOrNull()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    if (only?.isDirectory == true) ContextItem(Icons.Folder, "Open", hint = "Enter") { onOpen(only) }
    if (only?.canPreview == true) {
        ContextItem(Icons.Image, "Preview", hint = "Space") { onAction(SftpAction.OpenPreview(only)) }
    }
    ContextItem(Icons.Download, state.downloadLabel) { pickers.download(selected) }
    if (only != null) {
        ContextItem(Icons.Pencil, "Rename", hint = "F2") { onAction(SftpAction.RequestRename) }
        ContextItem(Icons.Copy, "Copy path") {
            // A name can hold line breaks, which would run as commands when pasted into a shell.
            scope.launch { clipboard.setClipEntry(ClipEntry(StringSelection(printableRemoteText(only.path)))) }
        }
    }
    MenuDivider()
    ContextItem(
        icon = Icons.Trash,
        label = if (only != null) "Delete" else "Delete ${selected.size} items",
        tint = Theme[colors][error],
    ) { onAction(SftpAction.RequestDelete) }
}

@Composable
private fun FolderMenu(state: SftpBrowserState, pickers: SftpPickers, onAction: (SftpAction) -> Unit) {
    ContextItem(Icons.NewFolder, "New folder") { onAction(SftpAction.RequestNewFolder) }
    ContextItem(Icons.Upload, state.uploadFilesLabel) { pickers.uploadFiles() }
    ContextItem(Icons.Upload, state.uploadFolderLabel) { pickers.uploadFolder() }
    MenuDivider()
    ContextItem(Icons.Refresh, "Refresh") { onAction(SftpAction.Refresh) }
    ContextItem(
        icon = if (state.showHidden) Icons.EyeClosed else Icons.EyeOpen,
        label = if (state.showHidden) "Hide dotfiles" else "Show dotfiles",
    ) { onAction(SftpAction.ToggleHidden) }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Theme[colors][separator]),
    )
}

@Composable
private fun ContextItem(
    icon: ImageVector,
    label: String,
    hint: String? = null,
    tint: Color = Theme[colors][textPrimary],
    onClick: () -> Unit,
) {
    MenuItem(onClick = onClick, contentPadding = ContextItemPadding) {
        UnstyledIcon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, color = tint, fontSize = NameFontSize, maxLines = 1, modifier = Modifier.weight(1f))
        hint?.let { Text(it, color = Theme[colors][textSecondary], fontSize = DetailFontSize, maxLines = 1) }
    }
}

@Composable
private fun HeaderRow(state: SftpBrowserState, showMode: Boolean, onAction: (SftpAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Theme[colors][surface])) {
        // Each cell's hover area sticks out past its column, while its text lines up with the rows below.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .padding(horizontal = RowInset + RowPadding - HeaderCellPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ColumnGap - HeaderCellPadding * 2),
        ) {
            HeaderCell("Name", SftpSortColumn.Name, state, onAction, Modifier.weight(1f), TextAlign.Start)
            HeaderCell("Size", SftpSortColumn.Size, state, onAction, Modifier.headerWidth(SizeColumnWidth), TextAlign.End)
            HeaderCell("Modified", SftpSortColumn.Modified, state, onAction, Modifier.headerWidth(ModifiedColumnWidth), TextAlign.Start)
            if (showMode) {
                Text(
                    "Permissions",
                    color = Theme[colors][textSecondary],
                    fontSize = DetailFontSize,
                    maxLines = 1,
                    modifier = Modifier.headerWidth(ModeColumnWidth).padding(horizontal = HeaderCellPadding),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
    }
}

private fun Modifier.headerWidth(column: Dp) = width(column + HeaderCellPadding * 2)

@Composable
private fun HeaderCell(
    label: String,
    column: SftpSortColumn,
    state: SftpBrowserState,
    onAction: (SftpAction) -> Unit,
    modifier: Modifier,
    align: TextAlign,
) {
    val sorted = state.sortColumn == column
    val arrow = when {
        !sorted -> ""
        state.sortAscending -> " ↑"
        else -> " ↓"
    }
    HoverButton(
        onClick = { onAction(SftpAction.Sort(column)) },
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label + arrow,
            color = Theme[colors][if (sorted) textPrimary else textSecondary],
            fontSize = DetailFontSize,
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = HeaderCellPadding, vertical = 2.dp),
        )
    }
}

/** One line that shows all of itself on hover when it doesn't fit. What a server sent must already be printable. */
@Composable
internal fun FittedText(
    displayed: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = NameFontSize,
) {
    var truncated by remember(displayed) { mutableStateOf(false) }
    Box(modifier = modifier) {
        Tooltip(text = displayed, enabled = truncated) {
            Text(
                displayed,
                color = color,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { truncated = it.hasVisualOverflow },
            )
        }
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = RowInset)
            .clip(RowShape)
            .background(
                when {
                    selected -> Theme[colors][primary].copy(alpha = if (hovered) SELECTED_HOVER_ALPHA else SELECTED_ALPHA)
                    hovered -> Theme[colors][hoverOverlay]
                    else -> Color.Transparent
                },
            )
            .hoverable(interactionSource)
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
            .padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnstyledIcon(
                imageVector = fileIcon(file),
                contentDescription = when {
                    file.isSymlink -> "Link"
                    file.isDirectory -> "Folder"
                    else -> "File"
                },
                tint = if (file.isDirectory) Theme[colors][primary] else Theme[colors][textSecondary],
                modifier = Modifier.size(16.dp),
            )
            FittedText(
                displayed = file.displayName,
                color = Theme[colors][if (file.isDotfile) textSecondary else textPrimary],
                modifier = Modifier.weight(1f),
            )
        }
        RowDetail(
            text = if (file.kind == RemoteFileKind.File) formatSize(file.size) else "",
            width = SizeColumnWidth,
            align = TextAlign.End,
        )
        RowDetail(text = formatModified(file.modifiedMillis), width = ModifiedColumnWidth)
        if (showMode) RowDetail(text = formatMode(file.permissions), width = ModeColumnWidth, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RowDetail(text: String, width: Dp, align: TextAlign = TextAlign.Start, fontFamily: FontFamily? = null) {
    Text(
        text,
        // Digits of one width, so sizes and dates line up down the column.
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        color = Theme[colors][textSecondary],
        fontSize = DetailFontSize,
        fontFamily = fontFamily,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}
