package com.sshellspace.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.feature.vault.connections.ConnectionsPage
import com.sshellspace.feature.vault.portforwarding.PortForwardingPage
import com.sshellspace.feature.vault.settings.SettingsPage
import com.sshellspace.feature.vault.snippets.SnippetsPage
import com.sshellspace.feature.vault.sshkeys.SshKeysPage

enum class VaultTabPage(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
) {
    Vault("Vault", Icons.Vault, ::ConnectionsPage),
    SshKeys("Ssh Keys", Icons.SshKeys, ::SshKeysPage),
    PortForwarding("Port Forwarding", Icons.PortForwarding, ::PortForwardingPage),
    Snippets("Snippets", Icons.Snippet, ::SnippetsPage),
    Settings("Settings", Icons.Settings, ::SettingsPage),
}
