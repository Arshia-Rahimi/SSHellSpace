package com.sshellspace.feature.terminal.ui.screen.ssh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.SecretPrompt

@Composable
fun SshTerminalTabScreen(viewModel: SshTerminalTabViewModel) {
    val state by viewModel.state.collectAsState()
    val onAction = viewModel::execute

    when (val current = state) {
        is SshTerminalState.Connected -> current.terminal.content(Modifier.fillMaxSize())
        is SshTerminalState.Connecting -> StatusCard(
            title = "Connecting to ${current.title}…",
            primaryAction = null,
            secondaryAction = "Cancel" to { onAction(SshTerminalAction.Cancel) },
        )
        is SshTerminalState.AwaitingHostKey -> HostKeyCard(current.prompt, onAction)
        is SshTerminalState.AwaitingSecret -> SecretCard(current.prompt, onAction)
        is SshTerminalState.AwaitingChallenge -> ChallengeCard(current.challenge, onAction)
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
private fun HostKeyCard(prompt: HostKeyPrompt, onAction: (SshTerminalAction) -> Unit) {
    val endpoint = if (prompt.port == DEFAULT_SSH_PORT) prompt.host else "${prompt.host}:${prompt.port}"
    Card {
        if (prompt.isChangedKey) {
            Text("Host key changed for $endpoint", color = Theme[colors][error])
            Text(
                "The key this server sent doesn't match the one saved in known_hosts. Someone could be " +
                    "intercepting the connection. Only continue if you know the server's key was changed.",
                color = Theme[colors][textSecondary],
            )
            Fingerprint("Saved ${prompt.keyType} key", prompt.expectedFingerprint.orEmpty())
            Fingerprint("Key sent now", prompt.fingerprint)
        } else {
            Text("Trust $endpoint?")
            Text(
                "You haven't connected to this host before. Check that the fingerprint matches the server's " +
                    "before trusting it. It will be saved to known_hosts.",
                color = Theme[colors][textSecondary],
            )
            Fingerprint("${prompt.keyType} key fingerprint", prompt.fingerprint)
        }
        Actions(
            primaryAction = (if (prompt.isChangedKey) "Replace key and connect" else "Trust and connect") to {
                onAction(SshTerminalAction.AnswerHostKey(trust = true))
            },
            secondaryAction = "Cancel" to { onAction(SshTerminalAction.AnswerHostKey(trust = false)) },
            destructive = prompt.isChangedKey,
        )
    }
}

@Composable
private fun SecretCard(prompt: SecretPrompt, onAction: (SshTerminalAction) -> Unit) {
    // Keyed on the prompt so a retry starts with an empty field.
    var value by remember(prompt) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(prompt) { focusRequester.requestFocus() }

    Card {
        Text(prompt.title)
        Text(prompt.message, color = Theme[colors][textSecondary])
        TextField(
            value = value,
            onValueChange = { value = it },
            label = prompt.label,
            isPassword = true,
            isError = prompt.error != null,
            focusRequester = focusRequester,
            onSubmit = { onAction(SshTerminalAction.SubmitSecret(it)) },
        )
        prompt.error?.let { Text(it, color = Theme[colors][error]) }
        Actions(
            primaryAction = "Continue" to { onAction(SshTerminalAction.SubmitSecret(value)) },
            secondaryAction = "Cancel" to { onAction(SshTerminalAction.Cancel) },
        )
    }
}

@Composable
private fun ChallengeCard(challenge: AuthChallenge, onAction: (SshTerminalAction) -> Unit) {
    val answers = remember(challenge) { mutableStateListOf(*Array(challenge.prompts.size) { "" }) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(challenge) { focusRequester.requestFocus() }
    val submit = { onAction(SshTerminalAction.SubmitChallenge(answers.toList())) }

    Card {
        Text(challenge.title)
        if (challenge.instruction.isNotBlank()) {
            Text(challenge.instruction, color = Theme[colors][textSecondary])
        }
        challenge.prompts.forEachIndexed { index, prompt ->
            TextField(
                value = answers[index],
                onValueChange = { answers[index] = it },
                label = prompt.text.trim(),
                isPassword = !prompt.echo,
                focusRequester = focusRequester.takeIf { index == 0 },
                onSubmit = { text ->
                    answers[index] = text
                    if (index == challenge.prompts.lastIndex) submit()
                },
            )
        }
        Actions(
            primaryAction = "Continue" to submit,
            secondaryAction = "Cancel" to { onAction(SshTerminalAction.Cancel) },
        )
    }
}

@Composable
private fun StatusCard(
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

@Composable
private fun Fingerprint(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Theme[colors][textSecondary])
        SelectionContainer {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Theme[colors][surfaceElevated])
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Actions(
    primaryAction: Pair<String, () -> Unit>?,
    secondaryAction: Pair<String, () -> Unit>? = null,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        secondaryAction?.let { (label, onClick) ->
            Button(
                onClick = onClick,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label)
            }
        }
        primaryAction?.let { (label, onClick) ->
            Button(
                onClick = onClick,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (destructive) Theme[colors][error] else Theme[colors][primary])
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, color = Theme[colors][onPrimary])
            }
        }
    }
}
