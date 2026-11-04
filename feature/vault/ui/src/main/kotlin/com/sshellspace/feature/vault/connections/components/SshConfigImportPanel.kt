package com.sshellspace.feature.vault.connections.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.warning
import com.sshellspace.feature.vault.components.SidebarPanel
import com.sshellspace.feature.vault.connections.ConnectionsPageAction
import com.sshellspace.feature.vault.connections.SshConfigImportState
import com.sshellspace.feature.vault.domain.SshConfigHost

private const val DISABLED_ALPHA = 0.5f

@Composable
internal fun SshConfigImportPanel(
    state: SshConfigImportState,
    onAction: (ConnectionsPageAction) -> Unit,
) {
    val canImport = state.selected.isNotEmpty() && !state.isImporting

    SidebarPanel(
        title = "Import from SSH config",
        onClose = { onAction(ConnectionsPageAction.CloseSidebar) },
        footer = {
            Button(
                onClick = { onAction(ConnectionsPageAction.ConfirmSshConfigImport) },
                enabled = canImport,
                modifier = Modifier.fillMaxWidth()
                    .alpha(if (canImport) 1f else DISABLED_ALPHA)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Theme[colors][primary])
                    .padding(vertical = 10.dp),
            ) {
                val count = state.selected.size
                Text(
                    if (state.isImporting) "Importing…" else "Import $count ${if (count == 1) "host" else "hosts"}",
                    color = Theme[colors][onPrimary],
                )
            }
        },
    ) {
        Text(
            "Copies hosts from ~/.ssh/config into this vault, this one time. Later edits to that file won't carry over.",
            color = Theme[colors][textSecondary],
        )
        if (state.hosts.isEmpty()) {
            Text("No hosts found in ~/.ssh/config.", color = Theme[colors][textSecondary])
        }
        state.hosts.forEach { host ->
            HostRow(
                host = host,
                selected = host.alias in state.selected,
                onToggle = { onAction(ConnectionsPageAction.ToggleSshConfigHost(host.alias)) },
            )
        }
    }
}

@Composable
private fun HostRow(host: SshConfigHost, selected: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(host.alias)
            Text(host.summary, color = Theme[colors][textSecondary])
            if (host.alreadySaved) {
                Text("Already in this vault", color = Theme[colors][textSecondary])
            }
            host.warnings.forEach { Text(it, color = Theme[colors][warning]) }
        }
        Switch(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = !host.alreadySaved,
            accessibilityLabel = "Import ${host.alias}",
        )
    }
}

private val SshConfigHost.summary: String
    get() = buildString {
        user?.let { append(it).append('@') }
        append(hostName)
        if (port != DEFAULT_SSH_PORT) append(':').append(port)
        proxyJump?.let { append(" via ").append(it) }
    }
