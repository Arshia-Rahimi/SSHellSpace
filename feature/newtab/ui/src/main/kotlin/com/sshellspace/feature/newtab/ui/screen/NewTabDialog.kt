package com.sshellspace.feature.newtab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.models.Shell

@Composable
fun NewTabDialog(
    viewModel: NewTabDialogViewModel,
    onCloseDialog: (() -> Unit)?,
) {
    val uiState by viewModel.uiState.collectAsState()
    val select = { action: NewTabDialogAction ->
        viewModel.execute(action)
        onCloseDialog?.invoke()
    }

    NewTabDialogContent(
        uiState = uiState,
        onSelectShell = { select(NewTabDialogAction.CreateNewTab(it)) },
        onSelectConnection = { select(NewTabDialogAction.Connect(it)) },
        onBrowseFiles = { select(NewTabDialogAction.BrowseFiles(it)) },
        onBrowseThisDevice = { select(NewTabDialogAction.BrowseThisDevice) },
    )
}

@Composable
private fun NewTabDialogContent(
    uiState: NewTabDialogState,
    onSelectShell: (Shell) -> Unit,
    onSelectConnection: (SshConnection) -> Unit,
    onBrowseFiles: (SshConnection) -> Unit,
    onBrowseThisDevice: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 480.dp)
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], shape)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                uiState.isLoading -> SectionLabel("Looking for shells…")
                uiState.defaultShell == null -> SectionLabel("No shells found")
                else -> {
                    SectionLabel("Default")
                    OptionRow(
                        title = uiState.defaultShell.name,
                        subtitle = uiState.defaultShell.command,
                        highlighted = true,
                        trailing = uiState.newTabShortcutLabel,
                        onClick = { onSelectShell(uiState.defaultShell) },
                    )
                }
            }
            if (uiState.connections.isNotEmpty()) {
                Separator()
                SectionLabel("Connections")
                uiState.connections.forEach { connection ->
                    OptionRow(
                        title = connection.name,
                        subtitle = connection.address,
                        highlighted = false,
                        trailing = null,
                        onClick = { onSelectConnection(connection) },
                        action = {
                            Tooltip(text = "Browse files") {
                                IconButton(
                                    onClick = { onBrowseFiles(connection) },
                                    imageVector = Icons.Folder,
                                    contentDescription = "Browse files on ${connection.name}",
                                )
                            }
                        },
                    )
                }
            }
            Separator()
            SectionLabel("Files")
            OptionRow(
                title = "This device",
                subtitle = "Browse this computer's files",
                highlighted = false,
                trailing = null,
                onClick = onBrowseThisDevice,
            )
            if (!uiState.isLoading && uiState.otherShells.isNotEmpty()) {
                Separator()
                SectionLabel("Other shells")
                uiState.otherShells.forEach { shell ->
                    OptionRow(
                        title = shell.name,
                        subtitle = shell.command,
                        highlighted = false,
                        trailing = null,
                        onClick = { onSelectShell(shell) },
                    )
                }
            }
        }
    }
}

/** `user@host`, plus the port when it isn't 22. */
private val SshConnection.address: String
    get() = if (port == DEFAULT_SSH_PORT) "$username@$host" else "$username@$host:$port"

@Composable
private fun Separator() {
    UnstyledHorizontalSeparator(
        color = Theme[colors][separator],
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Theme[colors][textSecondary],
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String?,
    highlighted: Boolean,
    trailing: String?,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (highlighted) Theme[colors][primary] else Color.Transparent, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title)
                subtitle?.let { Text(it, color = Theme[colors][textSecondary]) }
            }
            if (trailing != null) {
                Text(trailing, color = Theme[colors][textSecondary])
            }
            action?.invoke()
        }
    }
}
