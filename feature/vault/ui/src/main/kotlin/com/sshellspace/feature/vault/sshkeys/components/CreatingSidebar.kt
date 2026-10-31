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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Dropdown
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onOverlay
import com.sshellspace.core.ui.designsystem.theme.overlay
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.feature.vault.sshkeys.CreationMethod
import com.sshellspace.feature.vault.sshkeys.SidebarState
import com.sshellspace.feature.vault.sshkeys.SshKeysPageAction
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch

object CreatingSidebar {

    @Composable
    fun VaultKey(
        state: SidebarState.VaultKeyCreating,
        onAction: (SshKeysPageAction) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.CreateVault, onSelected = { onAction(SshKeysPageAction.SwitchMethod(it)) })
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.VaultKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            ErrorText(state.nameFieldError)
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
        onAction: (SshKeysPageAction) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.CreateDevice, onSelected = { onAction(SshKeysPageAction.SwitchMethod(it)) })
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            ErrorText(state.nameFieldError)
            TextField(
                value = state.fileName,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetFileName(it)) },
                label = "File name",
                placeholder = "optional, defaults to SSHellSpace_<name>",
                enabled = !isSaveInFlight,
            )
            PathField(
                value = state.path,
                onValueChange = { onAction(SshKeysPageAction.DeviceKey.SetPath(it)) },
                label = "Folder path",
                placeholder = "/home/user/.ssh",
                enabled = !isSaveInFlight,
                browseDescription = "Browse folders",
                icon = Icons.Folder,
                pick = { FileKit.openDirectoryPicker()?.file?.absolutePath },
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
        onAction: (SshKeysPageAction) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.Import, onSelected = { onAction(SshKeysPageAction.SwitchMethod(it)) })
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.ImportKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            ErrorText(state.nameFieldError)
            PathField(
                value = state.path,
                onValueChange = { onAction(SshKeysPageAction.ImportKey.SetPath(it)) },
                label = "File path",
                placeholder = "/home/user/.ssh/id_ed25519",
                enabled = !isSaveInFlight,
                browseDescription = "Browse files",
                icon = Icons.File,
                pick = { FileKit.openFilePicker()?.file?.absolutePath },
            )
            PassphraseField(
                value = state.passphrase,
                onValueChange = { onAction(SshKeysPageAction.ImportKey.SetPassphrase(it)) },
                visible = state.passphraseVisible,
                onToggleVisible = { onAction(SshKeysPageAction.ImportKey.TogglePassphraseVisibility(it)) },
                enabled = !isSaveInFlight,
            )
            Spacer(modifier = Modifier.weight(1f))
            SidebarFooter(
                label = "Import",
                isSaveInFlight = isSaveInFlight,
                onSave = { onAction(SshKeysPageAction.ImportKey.Save) },
            )
        }
    }

    @Composable
    fun PasteKey(
        state: SidebarState.PasteKeyCreating,
        onAction: (SshKeysPageAction) -> Unit,
        onClose: () -> Unit,
        isSaveInFlight: Boolean,
        errorMessage: String?,
    ) {
        SidebarShell(onClose = onClose) {
            MethodSelector(selected = CreationMethod.Paste, onSelected = { onAction(SshKeysPageAction.SwitchMethod(it)) })
            SidebarSeparator()
            SidebarError(errorMessage)
            TextField(
                value = state.nameField,
                onValueChange = { onAction(SshKeysPageAction.PasteKey.SetName(it)) },
                label = "Name",
                placeholder = "my-key",
                enabled = !isSaveInFlight,
            )
            ErrorText(state.nameFieldError)
            TextField(
                value = state.privateKey,
                onValueChange = { onAction(SshKeysPageAction.PasteKey.SetPrivateKey(it)) },
                label = "Private key",
                placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                enabled = !isSaveInFlight,
                singleLine = false,
                minLines = 6,
            )
            PassphraseField(
                value = state.passphrase,
                onValueChange = { onAction(SshKeysPageAction.PasteKey.SetPassphrase(it)) },
                visible = state.passphraseVisible,
                onToggleVisible = { onAction(SshKeysPageAction.PasteKey.TogglePassphraseVisibility(it)) },
                enabled = !isSaveInFlight,
            )
            Spacer(modifier = Modifier.weight(1f))
            SidebarFooter(
                label = "Import",
                isSaveInFlight = isSaveInFlight,
                onSave = { onAction(SshKeysPageAction.PasteKey.Save) },
            )
        }
    }
}

@Composable
private fun PathField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    browseDescription: String,
    icon: ImageVector,
    pick: suspend () -> String?,
) {
    val scope = rememberCoroutineScope()
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        trailing = {
            IconButton(
                onClick = { scope.launch { pick()?.let(onValueChange) } },
                enabled = enabled,
                imageVector = icon,
                contentDescription = browseDescription,
                tint = Theme[colors][textSecondary],
            )
        },
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: (Boolean) -> Unit,
    enabled: Boolean,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = "Passphrase",
        placeholder = "optional",
        isPassword = !visible,
        enabled = enabled,
        trailing = {
            IconButton(
                onClick = { onToggleVisible(!visible) },
                imageVector = if (visible) Icons.EyeClosed else Icons.EyeOpen,
                contentDescription = if (visible) "Hide passphrase" else "Show passphrase",
                tint = Theme[colors][textSecondary],
            )
        },
    )
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
            .background(Theme[colors][overlay])
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
        UnstyledHorizontalSeparator(thickness = 1.dp, color = Theme[colors][separator])
        content()
    }
}

@Composable
private fun MethodSelector(
    selected: CreationMethod,
    onSelected: (CreationMethod) -> Unit,
) {
    Dropdown(
        options = CreationMethod.entries.toList(),
        selected = selected,
        onSelected = onSelected,
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
    UnstyledHorizontalSeparator(thickness = 1.dp, color = Theme[colors][separator])
}

@Composable
private fun SidebarError(errorMessage: String?) {
    ErrorText(errorMessage)
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, color = Theme[colors][error])
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
            Text(label)
        }
    }
}
