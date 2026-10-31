package com.sshellspace.feature.vault.portforwarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.success
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textDisabled
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.warning
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.vault.portforwarding.label
import com.sshellspace.feature.vault.portforwarding.route

private val CardShape = RoundedCornerShape(8.dp)

@Composable
fun PortForwardCard(
    forward: PortForward,
    connectionName: String?,
    state: PortForwardState?,
    selected: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Theme[colors][surface])
            .border(1.dp, if (selected) Theme[colors][primary] else Color.Transparent, CardShape)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(state)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = forward.name,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Badge(forward.type.label)
                }
                Text(
                    text = forward.route,
                    fontFamily = FontFamily.Monospace,
                    color = Theme[colors][textSecondary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText(state, connectionName),
                    color = if (state is PortForwardState.Failed) Theme[colors][error] else Theme[colors][textSecondary],
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val running = state is PortForwardState.Active || state is PortForwardState.Starting
            Tooltip(text = if (running) "Stop" else "Start") {
                IconButton(
                    onClick = if (running) onStop else onStart,
                    enabled = running || connectionName != null,
                    imageVector = if (running) Icons.Stop else Icons.Start,
                    contentDescription = if (running) "Stop ${forward.name}" else "Start ${forward.name}",
                    tint = Theme[colors][primary],
                )
            }
        }
    }
}

private fun statusText(state: PortForwardState?, connectionName: String?): String {
    val via = connectionName?.let { "via $it" } ?: "Its connection was deleted"
    return when (state) {
        null -> via
        PortForwardState.Starting -> "Connecting $via…"
        is PortForwardState.Active -> "Listening on ${state.boundAddress} $via"
        is PortForwardState.Failed -> state.message
    }
}

@Composable
private fun StatusDot(state: PortForwardState?) {
    val color = when (state) {
        null -> Theme[colors][textDisabled]
        PortForwardState.Starting -> Theme[colors][warning]
        is PortForwardState.Active -> Theme[colors][success]
        is PortForwardState.Failed -> Theme[colors][error]
    }
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        color = Theme[colors][textSecondary],
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Theme[colors][surfaceElevated])
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
