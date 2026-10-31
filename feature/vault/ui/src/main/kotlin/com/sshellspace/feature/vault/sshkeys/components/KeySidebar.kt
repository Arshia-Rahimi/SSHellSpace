package com.sshellspace.feature.vault.sshkeys.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onOverlay
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.components.SidebarPanel
import com.sshellspace.feature.vault.sshkeys.SidebarState
import com.sshellspace.feature.vault.sshkeys.SshKeysPageAction
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KeySidebar(
    state: SidebarState.Editing,
    onAction: (SshKeysPageAction.Editing) -> Unit,
    onClose: () -> Unit,
    isSaveInFlight: Boolean,
    errorMessage: String?,
) {
    SidebarPanel(
        title = "Edit key",
        onClose = onClose,
        footer = {
            if (errorMessage != null) {
                Text(errorMessage, color = Theme[colors][error])
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onAction(SshKeysPageAction.Editing.Save) },
                    enabled = !isSaveInFlight && state.nameFieldError == null,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(Theme[colors][surfaceElevated])
                        .padding(vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        UnstyledIcon(
                            imageVector = Icons.Check,
                            contentDescription = null,
                            tint = Theme[colors][onOverlay],
                        )
                        Text("Save")
                    }
                }
                IconButton(
                    onClick = { onAction(SshKeysPageAction.Editing.RequestDelete) },
                    enabled = !isSaveInFlight,
                    imageVector = Icons.Trash,
                    contentDescription = "Delete key",
                )
            }
        },
    ) {
        Text("Type: ${state.key.type.name.lowercase().replaceFirstChar { it.uppercase() }}")
        Text("Algorithm: ${state.key.algorithm.name}")
        state.details?.path?.let { Text("Path: $it") }
        TextField(
            value = state.nameField,
            onValueChange = { onAction(SshKeysPageAction.Editing.SetName(it)) },
            label = "Name",
            placeholder = "my-key",
            enabled = !isSaveInFlight,
        )
        if (state.nameFieldError != null) {
            Text(state.nameFieldError, color = Theme[colors][error])
        }
        PublicKeyBox(state)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PublicKeyBox(state: SidebarState.Editing) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val publicKey = state.details?.publicKey

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Public key")
        when {
            state.isLoadingDetails -> Text("Loading...", color = Theme[colors][textSecondary])
            publicKey != null -> {
                SelectionContainer {
                    Text(
                        text = publicKey,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Theme[colors][surfaceElevated])
                            .padding(8.dp),
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(StringSelection(publicKey)))
                            copied = true
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Theme[colors][surfaceElevated])
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        UnstyledIcon(
                            imageVector = if (copied) Icons.Check else Icons.Copy,
                            contentDescription = null,
                            tint = Theme[colors][onOverlay],
                        )
                        Text(if (copied) "Copied" else "Copy public key")
                    }
                }
            }
            else -> Text(
                text = state.details?.error ?: "Public key unavailable",
                color = Theme[colors][error],
            )
        }
    }
}
