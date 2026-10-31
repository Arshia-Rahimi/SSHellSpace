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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuItem
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onSurface
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
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().weight(1f),
        ) {
            SshKeysHeader(
                searchQuery = uiState.searchQuery,
                onSearch = { onAction(SshKeysPageAction.SetSearch(it)) },
                onNewKey = { onAction(SshKeysPageAction.OpenCreate(it)) },
            )
            when {
                uiState.keys.isEmpty() -> EmptyState(
                    onGenerate = { onAction(SshKeysPageAction.OpenCreate(CreationMethod.CreateVault)) },
                    onImport = { onAction(SshKeysPageAction.OpenCreate(CreationMethod.Import)) },
                )
                uiState.filteredKeys.isEmpty() -> CenteredMessage("No keys match \"${uiState.searchQuery.trim()}\"")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
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
                                    (uiState.sidebarState as SidebarState.Editing).key.id == key.id,
                            onClick = { onAction(SshKeysPageAction.SelectKey(key)) },
                        )
                    }
                }
            }
        }
        AnimatedContent(
            targetState = uiState.sidebarState,
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
}

@Composable
private fun SshKeysHeader(
    searchQuery: String,
    onSearch: (String) -> Unit,
    onNewKey: (CreationMethod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
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
        NewKeyMenu(onNewKey)
    }
}

@Composable
private fun NewKeyMenu(onSelected: (CreationMethod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        anchor = {
            Button(onClick = { expanded = !expanded }) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UnstyledIcon(
                        imageVector = Icons.Plus,
                        contentDescription = null,
                        tint = Theme[colors][onSurface],
                    )
                    Text("Key")
                }
            }
        },
        panel = {
            DropdownMenuPanel(Modifier.width(200.dp)) {
                CreationMethod.entries.forEach { method ->
                    UnstyledDropdownMenuItem(
                        onClick = {
                            expanded = false
                            onSelected(method)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = method.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
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
