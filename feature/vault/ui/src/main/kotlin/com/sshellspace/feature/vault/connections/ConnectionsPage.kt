package com.sshellspace.feature.vault.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.connections.components.ConnectionCard
import com.sshellspace.feature.vault.connections.components.ConnectionSidebar
import org.koin.compose.viewmodel.koinViewModel

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
            compact = compact,
        )
        when {
            uiState.connections.isEmpty() -> EmptyState(onNew = { onAction(ConnectionsPageAction.OpenCreate) })
            uiState.filteredConnections.isEmpty() -> CenteredMessage(
                "No connections match \"${uiState.searchQuery.trim()}\"",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 16.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.filteredConnections,
                    key = { it.id },
                ) { connection ->
                    ConnectionCard(
                        connection = connection,
                        jumpHostName = connection.options.jumpHostId?.let(uiState::connectionName),
                        selected = uiState.sidebar?.editingId == connection.id,
                        onClick = { onAction(ConnectionsPageAction.Select(connection)) },
                        onConnect = { onAction(ConnectionsPageAction.Connect(connection)) },
                    )
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
    AnimatedContent(
        targetState = uiState.sidebar != null,
        modifier = modifier,
    ) { open ->
        if (open) {
            ConnectionSidebar(uiState, onAction)
        } else {
            Spacer(modifier = Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun Header(
    searchQuery: String,
    onSearch: (String) -> Unit,
    onNew: () -> Unit,
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
            placeholder = "Search by name, host or user",
        )
        PrimaryButton(
            label = if (compact) null else "Connection",
            contentDescription = "New connection",
            onClick = onNew,
        )
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
private fun EmptyState(onNew: () -> Unit) {
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
