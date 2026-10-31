package com.sshellspace.feature.vault.portforwarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.LOCALHOST
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Dropdown
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.vault.components.SidebarPanel
import com.sshellspace.feature.vault.portforwarding.PortForwardForm
import com.sshellspace.feature.vault.portforwarding.PortForwardingPageAction
import com.sshellspace.feature.vault.portforwarding.PortForwardingPageUiState
import com.sshellspace.feature.vault.portforwarding.bindLabel
import com.sshellspace.feature.vault.portforwarding.description
import com.sshellspace.feature.vault.portforwarding.destinationHint
import com.sshellspace.feature.vault.portforwarding.label

@Composable
fun PortForwardSidebar(
    uiState: PortForwardingPageUiState,
    onAction: (PortForwardingPageAction) -> Unit,
) {
    val state = uiState.sidebar ?: return
    val form = state.form
    val errors = uiState.formErrors
    val enabled = !uiState.isSaveInFlight
    val isRunning = state.editingId?.let { uiState.states[it] } is PortForwardState.Active
    val edit = { update: PortForwardForm.() -> PortForwardForm ->
        onAction(PortForwardingPageAction.EditForm { it.update() })
    }

    SidebarPanel(
        title = if (state.isNew) "New port forward" else "Edit port forward",
        onClose = { onAction(PortForwardingPageAction.CloseSidebar) },
        footer = {
            ErrorText(uiState.errorMessage)
            if (isRunning) {
                Text("Saving restarts the running forward.", color = Theme[colors][textSecondary])
            }
            Footer(isNew = state.isNew, enabled = enabled, onAction = onAction)
        },
    ) {
        Field(errors.connection) {
            if (uiState.connections.isEmpty()) {
                Text("No connections yet. Add one on the Vault page.", color = Theme[colors][textSecondary])
            } else {
                Dropdown(
                    options = uiState.connections,
                    selected = uiState.connections.firstOrNull { it.id == form.connectionId },
                    onSelected = { connection -> edit { copy(connectionId = connection?.id) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Connection",
                    enabled = enabled,
                    optionLabel = { it?.name ?: "Choose a connection" },
                )
            }
            if (form.connectionId != null && uiState.connectionName(form.connectionId) == null) {
                Text("The connection this forward used was deleted.", color = Theme[colors][error])
            }
        }

        Dropdown(
            options = PortForwardType.entries,
            selected = form.type,
            onSelected = { edit { copy(type = it) } },
            modifier = Modifier.fillMaxWidth(),
            label = "Type",
            enabled = enabled,
            optionLabel = { it.label },
        )
        Text(form.type.description, color = Theme[colors][textSecondary])

        AddressFields(
            host = form.bindHost,
            onHostChange = { edit { copy(bindHost = it) } },
            hostLabel = "Listen on",
            hostPlaceholder = LOCALHOST,
            port = form.bindPort,
            onPortChange = { edit { copy(bindPort = it) } },
            portLabel = form.type.bindLabel,
            portPlaceholder = if (form.type == PortForwardType.Dynamic) "1080" else "8080",
            error = errors.bind,
            enabled = enabled,
        )
        Text(
            text = if (form.type == PortForwardType.Remote) {
                "Most servers only listen on 127.0.0.1 unless GatewayPorts is on."
            } else {
                "Use 0.0.0.0 to let other devices on your network connect."
            },
            color = Theme[colors][textSecondary],
        )

        if (form.type != PortForwardType.Dynamic) {
            AddressFields(
                host = form.destinationHost,
                onHostChange = { edit { copy(destinationHost = it) } },
                hostLabel = "Destination host",
                hostPlaceholder = "localhost",
                port = form.destinationPort,
                onPortChange = { edit { copy(destinationPort = it) } },
                portLabel = "Port",
                portPlaceholder = "5432",
                error = errors.destination,
                enabled = enabled,
            )
            Text(form.type.destinationHint, color = Theme[colors][textSecondary])
        }

        Field(errors.name) {
            TextField(
                value = form.name,
                onValueChange = { edit { copy(name = it) } },
                label = "Name (optional)",
                placeholder = form.suggestedName,
                enabled = enabled,
                isError = errors.name != null,
            )
        }
    }
}

@Composable
private fun AddressFields(
    host: String,
    onHostChange: (String) -> Unit,
    hostLabel: String,
    hostPlaceholder: String,
    port: String,
    onPortChange: (String) -> Unit,
    portLabel: String,
    portPlaceholder: String,
    error: String?,
    enabled: Boolean,
) {
    Field(error) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = host,
                onValueChange = onHostChange,
                modifier = Modifier.weight(1f),
                label = hostLabel,
                placeholder = hostPlaceholder,
                enabled = enabled,
                isError = error != null && host.isBlank(),
            )
            TextField(
                value = port,
                onValueChange = { onPortChange(it.filter(Char::isDigit)) },
                modifier = Modifier.width(104.dp),
                label = portLabel,
                placeholder = portPlaceholder,
                enabled = enabled,
                isError = error != null,
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun Footer(
    isNew: Boolean,
    enabled: Boolean,
    onAction: (PortForwardingPageAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onAction(PortForwardingPageAction.Save) },
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
        Tooltip(text = if (isNew) "Create and start" else "Save and start", side = AnchorSide.Top) {
            IconButton(
                onClick = { onAction(PortForwardingPageAction.SaveAndStart) },
                enabled = enabled,
                imageVector = Icons.Start,
                contentDescription = if (isNew) "Create and start" else "Save and start",
                tint = Theme[colors][primary],
            )
        }
        if (!isNew) {
            Tooltip(text = "Delete", side = AnchorSide.Top) {
                IconButton(
                    onClick = { onAction(PortForwardingPageAction.RequestDelete) },
                    enabled = enabled,
                    imageVector = Icons.Trash,
                    contentDescription = "Delete port forward",
                )
            }
        }
    }
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
