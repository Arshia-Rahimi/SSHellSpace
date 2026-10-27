package com.sshellspace.feature.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import androidx.compose.ui.graphics.vector.ImageVector
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.feature.vault.sshkeys.SshKeysPage

enum class VaultTabPage(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
) {
    Vault("Vault", Icons.Vault, { ComingSoon() }),
    SshKeys("Ssh Keys", Icons.SshKeys, ::SshKeysPage),
    PortForwarding("Port Forwarding", Icons.PortForwarding, { ComingSoon() }),
}

@Composable
private fun ComingSoon() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Coming soon", color = Theme[colors][textSecondary])
    }
}
