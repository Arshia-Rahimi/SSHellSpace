package com.sshellspace.feature.vault.sshkeys

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.feature.vault.sshkeys.components.CreatingSidebar
import com.sshellspace.feature.vault.sshkeys.components.KeySidebar
import com.sshellspace.feature.vault.sshkeys.components.SshKeyCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SshKeysPage() {
    val viewModel = koinViewModel<SshKeysPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::execute

    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().weight(1f),
        ) {
            SshKeysHeader(
                onNewKey = { onAction(SshKeysPageAction.CreateNewKey) },
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.keys,
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
        AnimatedContent(
            targetState = uiState.sidebarState,
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
            }
        }
    }
}

@Composable
private fun SshKeysHeader(
    onNewKey: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("SSH Keys")
        IconButton(
            onClick = onNewKey,
            imageVector = Icons.Plus,
            contentDescription = "Create new SSH key",
        )
    }
}
