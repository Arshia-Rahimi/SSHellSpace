package com.sshellspace.feature.vault.sshkeys

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.sshkeys.components.CreatingSidebar
import com.sshellspace.feature.vault.sshkeys.components.KeySidebar
import com.sshellspace.feature.vault.sshkeys.components.SshKeyCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SshKeysPage() {
    val viewModel = koinViewModel<SshKeysPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    SshKeysPageContent(uiState, viewModel::execute)
}

@Composable
internal fun SshKeysPageContent(
    uiState: SshKeysPageUiState,
    onAction: (SshKeysPageAction) -> Unit,
) {
    val compact = currentWindowClass.isCompact

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            KeyList(
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
private fun KeyList(
    uiState: SshKeysPageUiState,
    onAction: (SshKeysPageAction) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SshKeysHeader(
            searchQuery = uiState.searchQuery,
            onSearch = { onAction(SshKeysPageAction.SetSearch(it)) },
            onNewKey = { onAction(SshKeysPageAction.OpenCreate(it)) },
            compact = compact,
        )
        when {
            uiState.keys.isEmpty() -> EmptyState(
                onGenerate = { onAction(SshKeysPageAction.OpenCreate(CreationMethod.CreateVault)) },
                onImport = { onAction(SshKeysPageAction.OpenCreate(CreationMethod.Import)) },
            )
            uiState.filteredKeys.isEmpty() -> CenteredMessage("No keys match \"${uiState.searchQuery.trim()}\"")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 16.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.filteredKeys,
                    key = { it.id },
                ) { key ->
                    SshKeyCard(
                        key = key,
                        selected = uiState.sidebarState is SidebarState.Editing &&
                                uiState.sidebarState.key.id == key.id,
                        onClick = { onAction(SshKeysPageAction.SelectKey(key)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    uiState: SshKeysPageUiState,
    onAction: (SshKeysPageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uiState.sidebarState,
        modifier = modifier,
        contentKey = { it.contentKey() },
    ) { state ->
        when (state) {
            SidebarState.None -> Spacer(modifier = Modifier.fillMaxHeight())
            is SidebarState.Editing -> KeySidebar(
                state = state,
                onAction = { onAction(it) },
                onClose = { onAction(SshKeysPageAction.CloseSidebar) },
                isSaveInFlight = uiState.isSaveInFlight,
                errorMessage = uiState.errorMessage,
            )
            is SidebarState.VaultKeyCreating -> CreatingSidebar.VaultKey(
                state = state,
                onAction = { onAction(it) },
                onClose = { onAction(SshKeysPageAction.CloseSidebar) },
                isSaveInFlight = uiState.isSaveInFlight,
                errorMessage = uiState.errorMessage,
            )
            is SidebarState.DeviceKeyCreating -> CreatingSidebar.DeviceKey(
                state = state,
                onAction = { onAction(it) },
                onClose = { onAction(SshKeysPageAction.CloseSidebar) },
                isSaveInFlight = uiState.isSaveInFlight,
                errorMessage = uiState.errorMessage,
            )
            is SidebarState.ImportKeyCreating -> CreatingSidebar.ImportKey(
                state = state,
                onAction = { onAction(it) },
                onClose = { onAction(SshKeysPageAction.CloseSidebar) },
                isSaveInFlight = uiState.isSaveInFlight,
                errorMessage = uiState.errorMessage,
            )
            is SidebarState.PasteKeyCreating -> CreatingSidebar.PasteKey(
                state = state,
                onAction = { onAction(it) },
                onClose = { onAction(SshKeysPageAction.CloseSidebar) },
                isSaveInFlight = uiState.isSaveInFlight,
                errorMessage = uiState.errorMessage,
            )
        }
    }
}

@Composable
private fun SshKeysHeader(
    searchQuery: String,
    onSearch: (String) -> Unit,
    onNewKey: (CreationMethod) -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SSH Keys")
        TextField(
            value = searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.weight(1f),
            placeholder = "Search keys",
        )
        NewKeyMenu(onSelected = onNewKey, iconOnly = compact)
    }
}

@Composable
private fun NewKeyMenu(onSelected: (CreationMethod) -> Unit, iconOnly: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        alignment = AnchorAlignment.End,
        sideOffset = 4.dp,
        anchor = {
            Button(
                onClick = { expanded = !expanded },
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
                        contentDescription = if (iconOnly) "New key" else null,
                        tint = Theme[colors][onPrimary],
                    )
                    if (!iconOnly) {
                        Text("Key", color = Theme[colors][onPrimary])
                    }
                }
            }
        },
        panel = {
            MenuPanel(width = 200.dp, onDismiss = { expanded = false }) {
                CreationMethod.entries.forEach { method ->
                    MenuItem(onClick = { onSelected(method) }) {
                        Text(method.label)
                    }
                }
            }
        },
    )
}

@Composable
private fun EmptyState(
    onGenerate: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No SSH keys yet")
        Text("Generate a new key or import an existing one.", color = Theme[colors][textSecondary])
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStateButton("Generate key", onGenerate)
            EmptyStateButton("Import key", onImport)
        }
    }
}

@Composable
private fun EmptyStateButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Theme[colors][surfaceElevated])
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label)
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

private fun SidebarState.contentKey(): Any = when (this) {
    SidebarState.None -> "none"
    is SidebarState.Editing -> key.id
    is SidebarState.Creating -> method
}
