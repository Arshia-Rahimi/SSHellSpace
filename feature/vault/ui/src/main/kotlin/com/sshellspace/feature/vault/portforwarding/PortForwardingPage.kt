package com.sshellspace.feature.vault.portforwarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.scrim
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.ui.screen.ssh.SshChallengeCard
import com.sshellspace.feature.terminal.ui.screen.ssh.SshHostKeyCard
import com.sshellspace.feature.terminal.ui.screen.ssh.SshSecretCard
import com.sshellspace.feature.vault.portforwarding.components.PortForwardCard
import com.sshellspace.feature.vault.portforwarding.components.PortForwardSidebar
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PortForwardingPage() {
    val viewModel = koinViewModel<PortForwardingPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    PortForwardingPageContent(uiState, viewModel::execute)
}

@Composable
internal fun PortForwardingPageContent(
    uiState: PortForwardingPageUiState,
    onAction: (PortForwardingPageAction) -> Unit,
) {
    val compact = currentWindowClass.isCompact

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            ForwardList(
                uiState = uiState,
                onAction = onAction,
                compact = compact,
                modifier = Modifier.fillMaxHeight().weight(1f),
            )
            if (!compact) {
                Sidebar(uiState, onAction)
            }
        }
        // Not enough room for the list and the sidebar side by side, so the sidebar floats over the list.
        if (compact) {
            Sidebar(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier.align(Alignment.CenterEnd).shadow(16.dp),
            )
        }
        uiState.login?.let { LoginOverlay(it, onAction) }
    }
}

@Composable
private fun ForwardList(
    uiState: PortForwardingPageUiState,
    onAction: (PortForwardingPageAction) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Port Forwarding", modifier = Modifier.weight(1f))
            if (uiState.forwards.isNotEmpty()) {
                PrimaryButton(
                    label = if (compact) null else "Forward",
                    contentDescription = "New port forward",
                    onClick = { onAction(PortForwardingPageAction.OpenCreate) },
                )
            }
        }
        when {
            uiState.forwards.isEmpty() -> EmptyState(
                hasConnections = uiState.connections.isNotEmpty(),
                onNew = { onAction(PortForwardingPageAction.OpenCreate) },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 16.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.forwards, key = { it.id }) { forward ->
                    PortForwardCard(
                        forward = forward,
                        connectionName = uiState.connectionName(forward.connectionId),
                        state = uiState.states[forward.id],
                        selected = uiState.sidebar?.editingId == forward.id,
                        onClick = { onAction(PortForwardingPageAction.Select(forward)) },
                        onStart = { onAction(PortForwardingPageAction.Start(forward)) },
                        onStop = { onAction(PortForwardingPageAction.Stop(forward)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    uiState: PortForwardingPageUiState,
    onAction: (PortForwardingPageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uiState.sidebar != null,
        modifier = modifier,
    ) { open ->
        if (open) {
            PortForwardSidebar(uiState, onAction)
        } else {
            Spacer(modifier = Modifier.fillMaxHeight())
        }
    }
}

/** Login questions for a forward that is starting, over the page so the rest can't be clicked meanwhile. */
@Composable
private fun LoginOverlay(login: LoginPrompt, onAction: (PortForwardingPageAction) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][scrim])
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        when (login) {
            is LoginPrompt.HostKey -> SshHostKeyCard(login.prompt) {
                onAction(PortForwardingPageAction.AnswerHostKey(trust = it))
            }
            is LoginPrompt.Secret -> SshSecretCard(
                prompt = login.prompt,
                onSubmit = { onAction(PortForwardingPageAction.SubmitSecret(it)) },
                onCancel = { onAction(PortForwardingPageAction.CancelLogin) },
            )
            is LoginPrompt.Challenge -> SshChallengeCard(
                challenge = login.challenge,
                onSubmit = { onAction(PortForwardingPageAction.SubmitChallenge(it)) },
                onCancel = { onAction(PortForwardingPageAction.CancelLogin) },
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Theme[colors][primary]),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UnstyledIcon(
                imageVector = Icons.Plus,
                contentDescription = if (label == null) contentDescription else null,
                tint = Theme[colors][onPrimary],
            )
            if (label != null) {
                Text(label, color = Theme[colors][onPrimary])
            }
        }
    }
}

@Composable
private fun EmptyState(hasConnections: Boolean, onNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No port forwards yet")
        Text(
            text = if (hasConnections) {
                "Pick a saved connection and the ports to forward through it."
            } else {
                "Port forwards run over a saved connection. Add one on the Vault page first."
            },
            color = Theme[colors][textSecondary],
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (hasConnections) {
            PrimaryButton(label = "New port forward", contentDescription = "New port forward", onClick = onNew)
        }
    }
}
