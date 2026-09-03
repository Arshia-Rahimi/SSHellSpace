package com.sshellspace.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.feature.vault.sshkeys.SshKeysPage

enum class VaultTabPage(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
) {
    Vault("Vault", Icons.Vault, {}),
    SshKeys("Ssh Keys", Icons.SshKeys, ::SshKeysPage),
    PortForwarding("Port Forwarding", Icons.PortForwarding, {}),
}
