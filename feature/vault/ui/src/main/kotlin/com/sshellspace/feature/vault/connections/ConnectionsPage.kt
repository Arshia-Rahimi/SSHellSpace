package com.sshellspace.feature.vault.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.connections.components.ConnectionCard
import com.sshellspace.feature.vault.connections.components.ConnectionSidebar
import com.sshellspace.feature.vault.connections.components.SshConfigImportPanel
import org.koin.compose.viewmodel.koinViewModel

private const val COLLAPSED_ROTATION = -90f

@Composable
fun ConnectionsPage() {
    val viewModel = koinViewModel<ConnectionsPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    ConnectionsPageContent(uiState, viewModel::execute)
}

@Composable
internal fun ConnectionsPageContent(
    uiState: ConnectionsPageUiState,
    onAction: (ConnectionsPageAction) -> Unit,
) {
    val compact = currentWindowClass.isCompact

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            ConnectionList(
                uiState = uiState,
                onAction = onAction,
                compact = compact,
                modifier = Modifier.fillMaxHeight().weight(1f),
            )
            if (!compact) {
                Sidebar(uiState, onAction)
            }
        }
        // Not enough room for the list and the sidebar side by side, so the sidebar floats over the list.
        if (compact) {
            Sidebar(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier.align(Alignment.CenterEnd).shadow(16.dp),
            )
        }
    }
}

@Composable
private fun ConnectionList(
    uiState: ConnectionsPageUiState,
    onAction: (ConnectionsPageAction) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Header(
            searchQuery = uiState.searchQuery,
            onSearch = { onAction(ConnectionsPageAction.SetSearch(it)) },
            onNew = { onAction(ConnectionsPageAction.OpenCreate) },
            onImport = { onAction(ConnectionsPageAction.OpenSshConfigImport) },
            compact = compact,
        )
        uiState.notice?.let { notice ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(notice, color = Theme[colors][textSecondary], modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onAction(ConnectionsPageAction.DismissNotice) },
                    imageVector = Icons.Close,
                    contentDescription = "Dismiss",
                )
            }
        }
        if (uiState.allTags.isNotEmpty()) TagFilter(uiState, onAction, compact)
        when {
            uiState.connections.isEmpty() -> EmptyState(
                onNew = { onAction(ConnectionsPageAction.OpenCreate) },
                onImport = { onAction(ConnectionsPageAction.OpenSshConfigImport) },
            )
            uiState.filteredConnections.isEmpty() -> CenteredMessage(
                "No connections match this search or tag",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 16.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sections = uiState.sections
                sections.forEach { section ->
                    // No heading when nothing is grouped, so a simple list stays simple.
                    if (sections.size > 1 || section.name != null) {
                        item(key = "group:${section.name}", span = { GridItemSpan(maxLineSpan) }) {
                            GroupHeading(
                                name = section.name,
                                count = section.connections.size,
                                collapsed = section.name in uiState.collapsedGroups,
                                onToggle = { section.name?.let { onAction(ConnectionsPageAction.ToggleGroup(it)) } },
                            )
                        }
                    }
                    if (section.name !in uiState.collapsedGroups) {
                        items(items = section.connections, key = { it.id }) { connection ->
                            ConnectionCard(
                                connection = connection,
                                jumpHostName = connection.options.jumpHostId?.let(uiState::connectionName),
                                selected = uiState.sidebar?.editingId == connection.id,
                                onClick = { onAction(ConnectionsPageAction.Select(connection)) },
                                onConnect = { onAction(ConnectionsPageAction.Connect(connection)) },
                                onBrowseFiles = { onAction(ConnectionsPageAction.BrowseFiles(connection)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    uiState: ConnectionsPageUiState,
    onAction: (ConnectionsPageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val import = uiState.sshConfigImport
    AnimatedContent(
        targetState = uiState.sidebar != null || import != null,
        modifier = modifier,
    ) { open ->
        when {
            !open -> Spacer(modifier = Modifier.fillMaxHeight())
            import != null -> SshConfigImportPanel(import, onAction)
            else -> ConnectionSidebar(uiState, onAction)
        }
    }
}

@Composable
private fun Header(
    searchQuery: String,
    onSearch: (String) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connections")
        TextField(
            value = searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.weight(1f),
            placeholder = "Search by name, host, user, group or tag",
        )
        SecondaryButton(if (compact) "Import" else "Import SSH config", onImport)
        PrimaryButton(
            label = if (compact) null else "Connection",
            contentDescription = "New connection",
            onClick = onNew,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun TagFilter(
    uiState: ConnectionsPageUiState,
    onAction: (ConnectionsPageAction) -> Unit,
    compact: Boolean,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TagChip("All", uiState.selectedTag == null) { onAction(ConnectionsPageAction.SetTagFilter(null)) }
        uiState.allTags.forEach { tag ->
            TagChip(tag, uiState.selectedTag == tag) {
                onAction(ConnectionsPageAction.SetTagFilter(tag.takeUnless { uiState.selectedTag == it }))
            }
        }
    }
}

@Composable
private fun TagChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick,
        modifier = Modifier.clip(shape)
            .background(if (selected) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (selected) Theme[colors][primary] else Theme[colors][border], shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(label, color = if (selected) Theme[colors][textPrimary] else Theme[colors][textSecondary])
    }
}

@Composable
private fun GroupHeading(name: String?, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        enabled = name != null,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (name != null) {
                UnstyledIcon(
                    imageVector = Icons.ChevronDown,
                    contentDescription = if (collapsed) "Expand $name" else "Collapse $name",
                    tint = Theme[colors][textSecondary],
                    modifier = Modifier.rotate(if (collapsed) COLLAPSED_ROTATION else 0f),
                )
            }
            Text(name ?: "Other", color = Theme[colors][textSecondary])
            Text("$count", color = Theme[colors][textSecondary])
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Theme[colors][primary]),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UnstyledIcon(
                imageVector = Icons.Plus,
                contentDescription = if (label == null) contentDescription else null,
                tint = Theme[colors][onPrimary],
            )
            if (label != null) {
                Text(label, color = Theme[colors][onPrimary])
            }
        }
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No connections yet")
        Text(
            "Save a host with its login, keys and options to connect in one click.",
            color = Theme[colors][textSecondary],
        )
        PrimaryButton(label = "New connection", contentDescription = "New connection", onClick = onNew)
        SecondaryButton("Import from ~/.ssh/config", onImport)
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = Theme[colors][textSecondary])
    }
}
