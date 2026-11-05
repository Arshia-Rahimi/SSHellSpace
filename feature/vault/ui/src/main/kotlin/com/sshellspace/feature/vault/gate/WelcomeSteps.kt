package com.sshellspace.feature.vault.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.label

private class WelcomeStep(val icon: ImageVector, val title: String, val text: String)

private val Steps = listOf(
    WelcomeStep(
        icon = Icons.Terminal,
        title = "Welcome to SSHellSpace",
        text = "An SSH client for your desktop, with a terminal, a file browser and port forwarding.",
    ),
    WelcomeStep(
        icon = Icons.Vault,
        title = "Your data stays in a vault",
        text = "Hosts, keys and saved passwords live in an encrypted file on this computer. Set a password to " +
            "protect it, and keep it safe: a forgotten password can't be recovered.",
    ),
    WelcomeStep(
        icon = Icons.Host,
        title = "Then add your servers",
        text = "Once your vault is open, choose New connection on the Vault page to save a host. After that, " +
            "${AppShortcut.QuickConnect.defaultChord.label} finds your hosts and snippets from anywhere.",
    ),
)

/** A short introduction before creating a first vault. Nothing here is required, so every step can be skipped. */
@Composable
internal fun WelcomeSteps(onAction: (VaultGateAction) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = Steps[index]
    val finish = { onAction(VaultGateAction.FinishWelcome) }

    UnstyledIcon(
        imageVector = step.icon,
        contentDescription = null,
        tint = Theme[colors][primary],
        modifier = Modifier.size(24.dp),
    )
    Heading(title = step.title, subtitle = step.text)
    StepDots(current = index, count = Steps.size)
    PrimaryButton(
        label = if (index == Steps.lastIndex) "Create your vault" else "Next",
        enabled = true,
        onClick = { if (index == Steps.lastIndex) finish() else index++ },
    )
    if (index == 0) {
        GateButton("Skip", onClick = finish)
    } else {
        GateButton("Back", onClick = { index-- })
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { step ->
            Box(
                Modifier.size(6.dp)
                    .clip(CircleShape)
                    .background(Theme[colors][if (step == current) primary else border]),
            )
        }
    }
}
