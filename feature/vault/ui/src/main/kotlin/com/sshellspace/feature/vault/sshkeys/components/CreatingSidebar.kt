package com.sshellspace.feature.vault.sshkeys.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.UnstyledVerticalSeparator
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Dropdown
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.feature.vault.sshkeys.CreationMethod
import com.sshellspace.feature.vault.sshkeys.SidebarState
import com.sshellspace.feature.vault.sshkeys.SshKeysPageAction

object CreatingSidebar {

    @Composable
    fun VaultKey(
        state: SidebarState.VaultKeyCreating,
        onAction: (SshKeysPageAction.VaultKey) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.CreateVault)
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.VaultKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            if (state.nameFieldError != null) {
                Text(state.nameFieldError)
            }
            TextField(
                value = state.comment,
                onValueChange = { onAction(SshKeysPageAction.VaultKey.SetComment(it)) },
                label = "Comment",
                placeholder = "optional",
                enabled = !isSaveInFlight,
            )
            AlgorithmDropdown(
                selected = state.algorithm,
                onSelected = { onAction(SshKeysPageAction.VaultKey.SetAlgorithm(it)) },
            )
            Spacer(modifier = Modifier.weight(1f))
            SidebarFooter(
                label = "Create",
                isSaveInFlight = isSaveInFlight,
                onSave = { onAction(SshKeysPageAction.VaultKey.Save) },
            )
        }
    }

    @Composable
    fun DeviceKey(
        state: SidebarState.DeviceKeyCreating,
        onAction: (SshKeysPageAction.DeviceKey) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.CreateDevice)
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            if (state.nameFieldError != null) {
                Text(state.nameFieldError)
            }
            TextField(
                value = state.fileName,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetFileName(it)) },
                label = "File name",
                placeholder = "optional, defaults to SSHellSpace_<name>",
                enabled = !isSaveInFlight,
            )
            TextField(
                value = state.path,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetPath(it)) },
                label = "Folder path",
                placeholder = "/home/user/.ssh",
                enabled = !isSaveInFlight,
            )
            TextField(
                value = state.comment,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetComment(it)) },
                label = "Comment",
                placeholder = "optional",
                enabled = !isSaveInFlight,
            )
            AlgorithmDropdown(
                selected = state.algorithm,
                onSelected = { onAction(SshKeysPageAction.DeviceKey.SetAlgorithm(it)) },
            )
            Spacer(modifier = Modifier.weight(1f))
            SidebarFooter(
                label = "Create",
                isSaveInFlight = isSaveInFlight,
                onSave = { onAction(SshKeysPageAction.DeviceKey.Save) },
            )
        }
    }

    @Composable
    fun ImportKey(
        state: SidebarState.ImportKeyCreating,
        onAction: (SshKeysPageAction.ImportKey) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.Import)
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.ImportKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            if (state.nameFieldError != null) {
                Text(state.nameFieldError)
            }
            TextField(
                value = state.path,
                onValueChange = { onAction(SshKeysPageAction.ImportKey.SetPath(it)) },
                label = "File path",
                placeholder = "/home/user/.ssh/id_ed25519",
                enabled = !isSaveInFlight,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = state.passphrase,
                    onValueChange = { onAction(SshKeysPageAction.ImportKey.SetPassphrase(it)) },
                    label = "Passphrase",
                    placeholder = "optional",
                    enabled = !isSaveInFlight,
                )
                IconButton(
                    onClick = { onAction(SshKeysPageAction.ImportKey.TogglePassphraseVisibility(!state.passphraseVisible)) },
                    imageVector = if (state.passphraseVisible) Icons.EyeClosed else Icons.EyeOpen,
                    contentDescription = if (state.passphraseVisible) "Hide passphrase" else "Show passphrase",
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SidebarFooter(
                label = "Import",
                isSaveInFlight = isSaveInFlight,
                onSave = { onAction(SshKeysPageAction.ImportKey.Save) },
            )
        }
    }
}

@Composable
private fun SidebarShell(
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onClose,
                imageVector = Icons.Back,
                contentDescription = "Close sidebar",
            )
            Text("New SSH key")
        }
        UnstyledVerticalSeparator(thickness = 1.dp, color = Color.White)
        content()
    }
}

@Composable
private fun MethodSelector(
    selected: CreationMethod,
) {
    Dropdown(
        options = CreationMethod.entries.toList(),
        selected = selected,
        onSelected = {},
        label = "Method",
        optionLabel = { it.label },
    )
}

@Composable
private fun AlgorithmDropdown(
    selected: Algorithm,
    onSelected: (Algorithm) -> Unit,
) {
    Dropdown(
        options = Algorithm.entries.toList(),
        selected = selected,
        onSelected = onSelected,
        label = "Algorithm",
        optionLabel = { it.name },
    )
}

@Composable
private fun SidebarSeparator() {
    UnstyledVerticalSeparator(thickness = 1.dp, color = Color.White)
}

@Composable
private fun SidebarError(errorMessage: String?) {
    if (errorMessage != null) {
        Text(errorMessage)
    }
}

@Composable
private fun SidebarFooter(
    label: String,
    isSaveInFlight: Boolean,
    onSave: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = !isSaveInFlight,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UnstyledIcon(
                imageVector = Icons.Check,
                contentDescription = null,
                tint = Color.White,
            )
            Text(label)
        }
    }
}
