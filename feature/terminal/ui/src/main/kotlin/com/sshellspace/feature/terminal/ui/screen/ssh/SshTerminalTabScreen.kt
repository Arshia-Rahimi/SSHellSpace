package com.sshellspace.feature.terminal.ui.screen.ssh

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.textSecondary

@Composable
fun SshTerminalTabScreen(viewModel: SshTerminalTabViewModel) {
    val state by viewModel.state.collectAsState()
    val onAction = viewModel::execute

    when (val current = state) {
        is SshTerminalState.Connected -> current.terminal.content(Modifier.fillMaxSize())
        is SshTerminalState.Reconnecting -> StatusCard(
            title = "Connection to ${current.title} lost",
            message = listOfNotNull(
                current.lastError,
                "Reconnecting in ${current.secondsLeft}s (attempt ${current.attempt} of ${current.maxAttempts}). " +
                    "Scrollback from the old session is gone.",
            ).joinToString("\n"),
            primaryAction = "Reconnect now" to { onAction(SshTerminalAction.Reconnect) },
            secondaryAction = "Stop" to { onAction(SshTerminalAction.Cancel) },
        )
        is SshTerminalState.Connecting -> StatusCard(
            title = "Connecting to ${current.title}…",
            primaryAction = null,
            secondaryAction = "Cancel" to { onAction(SshTerminalAction.Cancel) },
        )
        is SshTerminalState.AwaitingHostKey -> SshHostKeyCard(current.prompt) {
            onAction(SshTerminalAction.AnswerHostKey(trust = it))
        }
        is SshTerminalState.AwaitingSecret -> SshSecretCard(
            prompt = current.prompt,
            onSubmit = { onAction(SshTerminalAction.SubmitSecret(it)) },
            onCancel = { onAction(SshTerminalAction.Cancel) },
        )
        is SshTerminalState.AwaitingChallenge -> SshChallengeCard(
            challenge = current.challenge,
            onSubmit = { onAction(SshTerminalAction.SubmitChallenge(it)) },
            onCancel = { onAction(SshTerminalAction.Cancel) },
        )
        is SshTerminalState.Failed -> StatusCard(
            title = "Couldn't connect",
            message = current.message,
            isError = true,
            primaryAction = "Try again" to { onAction(SshTerminalAction.Reconnect) },
        )
        SshTerminalState.Cancelled -> StatusCard(
            title = "Connection cancelled",
            primaryAction = "Connect" to { onAction(SshTerminalAction.Reconnect) },
        )
        is SshTerminalState.Disconnected -> StatusCard(
            title = "Connection closed",
            message = current.exitCode?.let { "The remote shell exited with code $it." },
            primaryAction = "Reconnect" to { onAction(SshTerminalAction.Reconnect) },
        )
    }
}

@Composable
internal fun StatusCard(
    title: String,
    message: String? = null,
    isError: Boolean = false,
    primaryAction: Pair<String, () -> Unit>?,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Card {
        Text(title, color = if (isError) Theme[colors][error] else Color.Unspecified)
        message?.let { Text(it, color = Theme[colors][textSecondary]) }
        Actions(primaryAction = primaryAction, secondaryAction = secondaryAction)
    }
}
