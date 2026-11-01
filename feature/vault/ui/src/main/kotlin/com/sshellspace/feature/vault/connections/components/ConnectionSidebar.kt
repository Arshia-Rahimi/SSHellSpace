package com.sshellspace.feature.vault.connections.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Dropdown
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.components.SidebarPanel
import com.sshellspace.feature.vault.connections.AuthMethod
import com.sshellspace.feature.vault.connections.ConnectionForm
import com.sshellspace.feature.vault.connections.ConnectionFormErrors
import com.sshellspace.feature.vault.connections.ConnectionsPageAction
import com.sshellspace.feature.vault.connections.ConnectionsPageUiState
import com.sshellspace.feature.vault.sshkeys.DEFAULT_SSH_DIR
import com.sshellspace.feature.vault.sshkeys.pickSshKeyFile
import java.io.File
import kotlinx.coroutines.launch

private val DefaultUsername: String = System.getProperty("user.name").orEmpty()

@Composable
fun ConnectionSidebar(
    uiState: ConnectionsPageUiState,
    onAction: (ConnectionsPageAction) -> Unit,
) {
    val state = uiState.sidebar ?: return
    val form = state.form
    val errors = uiState.formErrors
    val enabled = !uiState.isSaveInFlight
    val edit = { update: ConnectionForm.() -> ConnectionForm -> onAction(ConnectionsPageAction.EditForm { it.update() }) }

    SidebarPanel(
        title = if (state.isNew) "New connection" else "Edit connection",
        onClose = { onAction(ConnectionsPageAction.CloseSidebar) },
        footer = {
            ErrorText(uiState.errorMessage)
            Footer(isNew = state.isNew, enabled = enabled, onAction = onAction)
        },
    ) {
        Field(errors.name) {
            TextField(
                value = form.name,
                onValueChange = { edit { copy(name = it) } },
                label = "Name",
                placeholder = "Production server",
                enabled = enabled,
                isError = errors.name != null,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = form.host,
                    onValueChange = { edit { copy(host = it) } },
                    modifier = Modifier.weight(1f),
                    label = "Host",
                    placeholder = "example.com or 10.0.0.5",
                    enabled = enabled,
                    isError = errors.host != null,
                )
                TextField(
                    value = form.port,
                    onValueChange = { edit { copy(port = it.filter(Char::isDigit)) } },
                    modifier = Modifier.width(88.dp),
                    label = "Port",
                    enabled = enabled,
                    isError = errors.port != null,
                    keyboardType = KeyboardType.Number,
                )
            }
            ErrorText(errors.host)
            ErrorText(errors.port)
        }
        Field(errors.username) {
            TextField(
                value = form.username,
                onValueChange = { edit { copy(username = it) } },
                label = "Username",
                placeholder = DefaultUsername.ifEmpty { "root" },
                enabled = enabled,
                isError = errors.username != null,
            )
        }

        Separator()
        AuthSection(
            form = form,
            keys = uiState.keys,
            authError = errors.auth,
            secretVisible = state.secretVisible,
            enabled = enabled,
            edit = edit,
            onToggleSecret = { onAction(ConnectionsPageAction.ToggleSecretVisible) },
        )

        Separator()
        AdvancedToggle(
            expanded = state.showAdvanced,
            onToggle = { onAction(ConnectionsPageAction.ToggleAdvanced) },
        )
        if (state.showAdvanced) {
            AdvancedSection(
                form = form,
                errors = errors,
                jumpHosts = uiState.jumpHostCandidates.filter { it.id != state.editingId },
                missingJumpHost = form.jumpHostId != null && uiState.connectionName(form.jumpHostId) == null,
                enabled = enabled,
                edit = edit,
            )
        }
    }
}

@Composable
private fun AuthSection(
    form: ConnectionForm,
    keys: List<SshKeyInfo>,
    authError: String?,
    secretVisible: Boolean,
    enabled: Boolean,
    edit: (ConnectionForm.() -> ConnectionForm) -> Unit,
    onToggleSecret: () -> Unit,
) {
    Dropdown(
        options = AuthMethod.entries,
        selected = form.authMethod,
        onSelected = { edit { copy(authMethod = it) } },
        modifier = Modifier.fillMaxWidth(),
        label = "Authentication",
        enabled = enabled,
        optionLabel = { it.label },
    )
    Text(form.authMethod.description, color = Theme[colors][textSecondary])

    when (form.authMethod) {
        AuthMethod.Password, AuthMethod.KeyboardInteractive -> SecretField(
            value = form.password,
            onValueChange = { edit { copy(password = it) } },
            label = "Password",
            placeholder = "Ask when connecting",
            visible = secretVisible,
            onToggleVisible = onToggleSecret,
            enabled = enabled,
        )

        AuthMethod.StoredKey -> Field(authError) {
            if (keys.isEmpty()) {
                Text("No SSH keys yet. Add one on the SSH Keys page.", color = Theme[colors][textSecondary])
            } else {
                Dropdown(
                    options = keys,
                    selected = keys.firstOrNull { it.id == form.keyId },
                    onSelected = { key -> edit { copy(keyId = key?.id) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Key",
                    enabled = enabled,
                    optionLabel = { it?.let { key -> "${key.name} · ${key.algorithm.name}" } ?: "Choose a key" },
                )
            }
            if (form.keyId != null && keys.none { it.id == form.keyId }) {
                Text("The key this connection used was deleted.", color = Theme[colors][error])
            }
        }

        AuthMethod.KeyFile -> {
            Field(authError) {
                KeyFileField(
                    value = form.keyFilePath,
                    onValueChange = { edit { copy(keyFilePath = it) } },
                    enabled = enabled,
                    isError = authError != null,
                )
            }
            SecretField(
                value = form.keyFilePassphrase,
                onValueChange = { edit { copy(keyFilePassphrase = it) } },
                label = "Passphrase",
                placeholder = "Ask when connecting",
                visible = secretVisible,
                onToggleVisible = onToggleSecret,
                enabled = enabled,
            )
        }

        AuthMethod.Agent -> Unit
    }
}

@Composable
private fun AdvancedSection(
    form: ConnectionForm,
    errors: ConnectionFormErrors,
    jumpHosts: List<SshConnection>,
    missingJumpHost: Boolean,
    enabled: Boolean,
    edit: (ConnectionForm.() -> ConnectionForm) -> Unit,
) {
    Field(if (missingJumpHost) "The jump host this connection used was deleted." else null) {
        Dropdown(
            options = listOf(null) + jumpHosts,
            selected = jumpHosts.firstOrNull { it.id == form.jumpHostId },
            onSelected = { host -> edit { copy(jumpHostId = host?.id) } },
            modifier = Modifier.fillMaxWidth(),
            label = "Jump host",
            enabled = enabled,
            optionLabel = { it?.name ?: "None, connect directly" },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = form.connectTimeout,
                onValueChange = { edit { copy(connectTimeout = it.filter(Char::isDigit)) } },
                modifier = Modifier.weight(1f),
                label = "Timeout (s)",
                enabled = enabled,
                isError = errors.connectTimeout != null,
                keyboardType = KeyboardType.Number,
            )
            TextField(
                value = form.keepAliveInterval,
                onValueChange = { edit { copy(keepAliveInterval = it.filter(Char::isDigit)) } },
                modifier = Modifier.weight(1f),
                label = "Keep-alive (s)",
                placeholder = "0 = off",
                enabled = enabled,
                isError = errors.keepAliveInterval != null,
                keyboardType = KeyboardType.Number,
            )
        }
        ErrorText(errors.connectTimeout)
        ErrorText(errors.keepAliveInterval)
    }
    SwitchRow(
        title = "Compression",
        description = "Compress traffic. Helps on slow links, costs CPU on fast ones.",
        checked = form.compression,
        onCheckedChange = { edit { copy(compression = it) } },
        enabled = enabled,
    )
    TextField(
        value = form.startupCommand,
        onValueChange = { edit { copy(startupCommand = it) } },
        label = "Startup command",
        placeholder = "Runs after the shell opens, e.g. tmux attach",
        enabled = enabled,
    )
}

@Composable
private fun Footer(
    isNew: Boolean,
    enabled: Boolean,
    onAction: (ConnectionsPageAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onAction(ConnectionsPageAction.Save) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Theme[colors][primary])
                .padding(vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UnstyledIcon(Icons.Check, contentDescription = null, tint = Theme[colors][onPrimary])
                Text(if (isNew) "Create" else "Save", color = Theme[colors][onPrimary])
            }
        }
        Tooltip(text = if (isNew) "Create and connect" else "Save and connect", side = AnchorSide.Top) {
            IconButton(
                onClick = { onAction(ConnectionsPageAction.SaveAndConnect) },
                enabled = enabled,
                imageVector = Icons.Connect,
                contentDescription = if (isNew) "Create and connect" else "Save and connect",
                tint = Theme[colors][primary],
            )
        }
        if (!isNew) {
            Tooltip(text = "Duplicate", side = AnchorSide.Top) {
                IconButton(
                    onClick = { onAction(ConnectionsPageAction.Duplicate) },
                    enabled = enabled,
                    imageVector = Icons.Duplicate,
                    contentDescription = "Duplicate connection",
                )
            }
            Tooltip(text = "Delete", side = AnchorSide.Top) {
                IconButton(
                    onClick = { onAction(ConnectionsPageAction.RequestDelete) },
                    enabled = enabled,
                    imageVector = Icons.Trash,
                    contentDescription = "Delete connection",
                )
            }
        }
    }
}

@Composable
private fun AdvancedToggle(expanded: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Advanced options", modifier = Modifier.weight(1f))
            UnstyledIcon(
                imageVector = Icons.ChevronDown,
                contentDescription = if (expanded) "Hide advanced options" else "Show advanced options",
                tint = Theme[colors][textSecondary],
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title)
            Text(description, color = Theme[colors][textSecondary])
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            accessibilityLabel = title,
        )
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        isPassword = !visible,
        enabled = enabled,
        trailing = {
            IconButton(
                onClick = onToggleVisible,
                imageVector = if (visible) Icons.EyeClosed else Icons.EyeOpen,
                contentDescription = if (visible) "Hide $label" else "Show $label",
                tint = Theme[colors][textSecondary],
            )
        },
    )
}

@Composable
private fun KeyFileField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
) {
    val scope = rememberCoroutineScope()
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = "Private key file",
        placeholder = File(DEFAULT_SSH_DIR, "id_ed25519").path,
        enabled = enabled,
        isError = isError,
        trailing = {
            IconButton(
                onClick = { scope.launch { pickSshKeyFile()?.let(onValueChange) } },
                enabled = enabled,
                imageVector = Icons.File,
                contentDescription = "Browse files",
                tint = Theme[colors][textSecondary],
            )
        },
    )
}

/** A field followed by its validation message. */
@Composable
private fun Field(error: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        ErrorText(error)
    }
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, color = Theme[colors][error])
    }
}

@Composable
private fun Separator() {
    UnstyledHorizontalSeparator(thickness = 1.dp, color = Theme[colors][separator])
}
