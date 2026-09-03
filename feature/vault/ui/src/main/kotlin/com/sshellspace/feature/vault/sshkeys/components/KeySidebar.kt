package com.sshellspace.feature.vault.sshkeys.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.UnstyledVerticalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onOverlay
import com.sshellspace.core.ui.designsystem.theme.overlay
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.feature.vault.sshkeys.SidebarState
import com.sshellspace.feature.vault.sshkeys.SshKeysPageAction

@Composable
fun KeySidebar(
    state: SidebarState.Editing,
    onAction: (SshKeysPageAction.Editing) -> Unit,
    onClose: () -> Unit,
    isSaveInFlight: Boolean,
    errorMessage: String?,
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
            Text("Edit key")
        }
        UnstyledVerticalSeparator(thickness = 1.dp, color = Theme[colors][separator])
        Text("Type: ${state.key.type.name}")
        Text("Algorithm: ${state.key.algorithm.name}")
        TextField(
            value = state.nameField,
            onValueChange = { onAction(SshKeysPageAction.Editing.SetName(it)) },
            label = "Name",
            placeholder = "my-key",
            enabled = !isSaveInFlight,
        )
        if (state.nameFieldError != null) {
            Text(state.nameFieldError)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (errorMessage != null) {
            Text(errorMessage)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAction(SshKeysPageAction.Editing.Save) },
                enabled = !isSaveInFlight,
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
                onClick = { onAction(SshKeysPageAction.Editing.Delete) },
                enabled = !isSaveInFlight,
                imageVector = Icons.Trash,
                contentDescription = "Delete key",
            )
        }
    }
}
