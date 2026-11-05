package com.sshellspace.feature.vault.knownhosts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.KnownHost
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textDisabled
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.printableRemoteText
import org.koin.compose.viewmodel.koinViewModel

private val DetailFontSize = 12.sp

@Composable
fun KnownHostsPage() {
    val viewModel = koinViewModel<KnownHostsPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    // On each visit, since connecting and the ssh tool can both have changed the file.
    LaunchedEffect(Unit) { viewModel.execute(KnownHostsPageAction.RefreshDevice) }
    KnownHostsPageContent(uiState, viewModel::execute)
}

@Composable
internal fun KnownHostsPageContent(
    uiState: KnownHostsPageUiState,
    onAction: (KnownHostsPageAction) -> Unit,
) {
    val compact = currentWindowClass.isCompact
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Known hosts")
            TextField(
                value = uiState.searchQuery,
                onValueChange = { onAction(KnownHostsPageAction.SetSearch(it)) },
                modifier = Modifier.weight(1f),
                placeholder = "Search by host, key type or fingerprint",
            )
        }
        uiState.notice?.let { Notice(it) { onAction(KnownHostsPageAction.DismissNotice) } }
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section(
                key = "vault",
                title = "This vault",
                description = "Checked by logins that move with the vault: vault keys and passwords.",
                actions = { VaultActions(hasHosts = uiState.vaultHosts.isNotEmpty(), onAction) },
                total = uiState.vaultHosts.size,
                hosts = uiState.filteredVaultHosts,
                emptyMessage = "No hosts yet. They're added when you trust one while connecting, or by importing a file.",
                onRemove = { onAction(KnownHostsPageAction.Remove(it)) },
            )
            section(
                key = "device",
                title = "This device",
                description = "${printableRemoteText(uiState.device.path)}, shared with the ssh command. Checked by device keys, key files and the SSH agent.",
                actions = {
                    SmallButton("Refresh") { onAction(KnownHostsPageAction.RefreshDevice) }
                },
                total = uiState.device.hosts.size,
                hosts = uiState.filteredDeviceHosts,
                emptyMessage = uiState.device.error ?: "This file has no hosts yet.",
                onRemove = null,
            )
        }
    }
}

@Composable
private fun VaultActions(hasHosts: Boolean, onAction: (KnownHostsPageAction) -> Unit) {
    SmallButton("Import…") { onAction(KnownHostsPageAction.Import(::pickKnownHostsFile)) }
    SmallButton("Export…", enabled = hasHosts) { onAction(KnownHostsPageAction.Export(::pickKnownHostsExportFile)) }
}

private fun LazyListScope.section(
    key: String,
    title: String,
    description: String,
    actions: @Composable () -> Unit,
    total: Int,
    hosts: List<KnownHost>,
    emptyMessage: String,
    onRemove: ((KnownHost) -> Unit)?,
) {
    item(key = "$key:heading") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title)
                    Text("$total", color = Theme[colors][textSecondary])
                }
                Text(description, color = Theme[colors][textSecondary], fontSize = DetailFontSize)
            }
            actions()
        }
    }
    if (hosts.isEmpty()) {
        item(key = "$key:empty") {
            val message = if (total == 0) emptyMessage else "No hosts here match this search."
            Text(message, color = Theme[colors][textSecondary], modifier = Modifier.padding(vertical = 4.dp))
        }
    }
    items(hosts, key = { "$key:${it.line}" }) { host ->
        KnownHostRow(host, onRemove?.let { remove -> { remove(host) } })
    }
}

@Composable
private fun KnownHostRow(host: KnownHost, onRemove: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Theme[colors][surface])
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (host.hosts.isEmpty()) {
                    Tooltip(text = "known_hosts can store a host's name as a hash, so it can't be shown here.") {
                        Text("Hashed host name", color = Theme[colors][textSecondary])
                    }
                } else {
                    // Can come from an imported file or a crafted vault, so it mustn't be able to disguise itself.
                    Text(
                        printableRemoteText(host.hosts.joinToString(", ")),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (host.isRevoked) Badge("Revoked", Theme[colors][error])
                if (host.isCertificateAuthority) Badge("Certificate authority", Theme[colors][textSecondary])
            }
            Text(
                "${printableRemoteText(host.keyType)}  ${host.fingerprint}",
                color = Theme[colors][textSecondary],
                fontFamily = FontFamily.Monospace,
                fontSize = DetailFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        onRemove?.let {
            Tooltip(text = "Remove. You'll be asked to trust this host again next time.") {
                Button(onClick = it, modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(6.dp)) {
                    UnstyledIcon(
                        imageVector = Icons.Trash,
                        contentDescription = "Remove host",
                        tint = Theme[colors][onSurface],
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text,
        color = color,
        fontSize = DetailFontSize,
        maxLines = 1,
        modifier = Modifier.clip(shape).border(1.dp, color, shape).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.clip(shape).border(1.dp, Theme[colors][border], shape).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = Theme[colors][if (enabled) onSurface else textDisabled], fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Notice(notice: KnownHostsNotice, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            notice.message,
            color = Theme[colors][if (notice.isError) error else textSecondary],
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onDismiss, modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(4.dp)) {
            UnstyledIcon(
                imageVector = Icons.Close,
                contentDescription = "Dismiss",
                tint = Theme[colors][onSurface],
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
