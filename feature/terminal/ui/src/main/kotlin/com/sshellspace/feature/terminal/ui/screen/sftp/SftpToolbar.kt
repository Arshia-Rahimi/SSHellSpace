package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.LocalWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.hoverOverlay
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textDisabled
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.shortcuts.label
import com.sshellspace.core.ui.shortcuts.primaryChord
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.printableRemoteText

private val ToolbarControlHeight = 28.dp
private val ToolbarIconSize = 16.dp
private val FilterWidth = 200.dp
private val CompactFilterWidth = 124.dp
private val ButtonShape = RoundedCornerShape(4.dp)
private val FieldShape = RoundedCornerShape(6.dp)
private val PathFieldPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
private const val ACTIVE_ALPHA = 0.18f

/** A [Button] that shows when it's under the pointer or has keyboard focus. The design system's draws nothing itself. */
@Composable
internal fun HoverButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        contentAlignment = contentAlignment,
        modifier = modifier
            .clip(ButtonShape)
            .background(
                when {
                    active -> Theme[colors][primary].copy(alpha = ACTIVE_ALPHA)
                    enabled && (hovered || focused) -> Theme[colors][hoverOverlay]
                    else -> Color.Transparent
                },
            ),
        content = content,
    )
}

@Composable
internal fun Toolbar(
    state: SftpBrowserState,
    selected: List<RemoteFile>,
    focus: SftpFocus,
    pickers: SftpPickers,
    onAction: (SftpAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Theme[colors][surface])) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolbarButton(Icons.Up, "Up one folder", enabled = state.path != "/") { onAction(SftpAction.GoUp) }
            ToolbarButton(Icons.Home, "Home folder") { onAction(SftpAction.GoHome) }
            ToolbarButton(Icons.Refresh, "Refresh (${primaryChord(Key.R).label})") { onAction(SftpAction.Refresh) }
            if (focus.editingPath) {
                PathField(state.path, focus, onAction, Modifier.weight(1f).padding(horizontal = 6.dp))
            } else {
                PathSegments(state.path, focus, onAction, Modifier.weight(1f).padding(horizontal = 6.dp))
            }
            FilterField(
                text = state.filter,
                focus = focus,
                onAction = onAction,
                modifier = Modifier.width(if (LocalWindowClass.current.isCompact) CompactFilterWidth else FilterWidth),
            )
            ToolbarDivider()
            ToolbarButton(Icons.NewFolder, "New folder (${primaryChord(Key.N, shift = true).label})") {
                onAction(SftpAction.RequestNewFolder)
            }
            UploadMenu(state, pickers, focus)
            ToolbarButton(Icons.Download, state.downloadLabel, enabled = selected.isNotEmpty()) { pickers.download(selected) }
            ToolbarButton(Icons.Pencil, "Rename (F2)", enabled = selected.size == 1) { onAction(SftpAction.RequestRename) }
            ToolbarButton(Icons.Trash, "Delete", enabled = selected.isNotEmpty()) { onAction(SftpAction.RequestDelete) }
            ToolbarDivider()
            ToolbarButton(
                icon = if (state.showHidden) Icons.EyeOpen else Icons.EyeClosed,
                label = if (state.showHidden) "Hide dotfiles" else "Show dotfiles",
                active = state.showHidden,
            ) { onAction(SftpAction.ToggleHidden) }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
    }
}

@Composable
private fun ToolbarDivider() {
    Box(modifier = Modifier.padding(horizontal = 6.dp).width(1.dp).height(16.dp).background(Theme[colors][separator]))
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Tooltip(text = label, side = AnchorSide.Bottom) {
        HoverButton(onClick = onClick, enabled = enabled, active = active, modifier = Modifier.size(ToolbarControlHeight)) {
            UnstyledIcon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> Theme[colors][textDisabled]
                    active -> Theme[colors][primary]
                    else -> Theme[colors][onSurface]
                },
                modifier = Modifier.size(ToolbarIconSize),
            )
        }
    }
}

@Composable
private fun UploadMenu(state: SftpBrowserState, pickers: SftpPickers, focus: SftpFocus) {
    var expanded by remember { mutableStateOf(false) }
    val setExpanded = { value: Boolean ->
        expanded = value
        focus.menuOpen = value
    }
    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = setExpanded,
        sideOffset = 4.dp,
        anchor = { ToolbarButton(Icons.Upload, state.uploadLabel) { setExpanded(!expanded) } },
        panel = {
            MenuPanel(onDismiss = { setExpanded(false) }) {
                MenuItem(onClick = {
                    setExpanded(false)
                    pickers.uploadFiles()
                }) {
                    Text(state.uploadFilesLabel)
                }
                MenuItem(onClick = {
                    setExpanded(false)
                    pickers.uploadFolder()
                }) {
                    Text(state.uploadFolderLabel)
                }
            }
        },
    )
}

/** For typing a path. Closes when the keyboard goes anywhere else. */
@Composable
private fun PathField(path: String, focus: SftpFocus, onAction: (SftpAction) -> Unit, modifier: Modifier) {
    var text by remember { mutableStateOf(path) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = modifier
            .height(ToolbarControlHeight)
            .onFocusChanged {
                if (hadFocus && !it.hasFocus) focus.editingPath = false
                hadFocus = it.hasFocus
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    focus.list.requestFocus()
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
            contentPadding = PathFieldPadding,
            focusRequester = focusRequester,
            onSubmit = {
                focus.list.requestFocus()
                onAction(SftpAction.GoToPath(it))
            },
        )
    }
}

/** Clickable folders on the way to [path]. Clicking anywhere else on the bar is for typing one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PathSegments(path: String, focus: SftpFocus, onAction: (SftpAction) -> Unit, modifier: Modifier) {
    val scroll = rememberScrollState()
    // A deep path doesn't fit, and the end of it is the part that says where this is.
    LaunchedEffect(path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val segments = pathSegments(path)
    val fieldColor = Theme[colors][background]
    Row(
        modifier = modifier
            .height(ToolbarControlHeight)
            .clip(FieldShape)
            .background(fieldColor)
            .border(1.dp, Theme[colors][border], FieldShape)
            .onClick { focus.editingPath = true }
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
                modifier = Modifier.horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                segments.forEachIndexed { index, (label, target) ->
                    if (index > 1) {
                        UnstyledIcon(
                            imageVector = Icons.ChevronRight,
                            contentDescription = null,
                            tint = Theme[colors][textSecondary],
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    HoverButton(onClick = { onAction(SftpAction.Navigate(target)) }) {
                        Text(
                            label,
                            color = Theme[colors][if (index == segments.lastIndex) textPrimary else textSecondary],
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (scroll.canScrollBackward) {
                Box(
                    modifier = Modifier
                        .height(ToolbarControlHeight)
                        .width(20.dp)
                        .background(Brush.horizontalGradient(listOf(fieldColor, Color.Transparent))),
                )
            }
        }
        Tooltip(text = "Type a path (${primaryChord(Key.L).label})", side = AnchorSide.Bottom) {
            HoverButton(onClick = { focus.editingPath = true }, modifier = Modifier.size(22.dp)) {
                UnstyledIcon(
                    imageVector = Icons.Pencil,
                    contentDescription = "Type a path",
                    tint = Theme[colors][textSecondary],
                    modifier = Modifier.size(12.dp),
                )
            }
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
private fun FilterField(text: String, focus: SftpFocus, onAction: (SftpAction) -> Unit, modifier: Modifier) {
    // Moving on from typing goes to the first match, or stays on the row that was already current.
    val enterList = {
        focus.list.requestFocus()
        onAction(SftpAction.MoveSelection(0))
    }
    Box(
        modifier = modifier
            .height(ToolbarControlHeight)
            .onFocusChanged { focus.filterFocused = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onAction(SftpAction.SetFilter(""))
                        focus.list.requestFocus()
                        true
                    }
                    Key.DirectionDown -> {
                        enterList()
                        true
                    }
                    else -> false
                }
            },
    ) {
        TextField(
            value = text,
            onValueChange = { onAction(SftpAction.SetFilter(it)) },
            placeholder = "Filter (${primaryChord(Key.F).label})",
            contentPadding = PathFieldPadding,
            focusRequester = focus.filter,
            onSubmit = { enterList() },
            trailing = {
                if (text.isEmpty()) {
                    UnstyledIcon(
                        imageVector = Icons.Search,
                        contentDescription = null,
                        tint = Theme[colors][textSecondary],
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    HoverButton(
                        onClick = {
                            onAction(SftpAction.SetFilter(""))
                            // This button is about to go away, and would take the keyboard with it.
                            focus.filter.requestFocus()
                        },
                        modifier = Modifier.size(14.dp),
                    ) {
                        UnstyledIcon(
                            imageVector = Icons.Close,
                            contentDescription = "Clear filter",
                            tint = Theme[colors][textSecondary],
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            },
        )
    }
}
