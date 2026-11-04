package com.sshellspace.feature.vault.connections.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.connections.address
import com.sshellspace.feature.vault.connections.method

private val CardShape = RoundedCornerShape(8.dp)
private const val MAX_CARD_TAGS = 2

@Composable
fun ConnectionCard(
    connection: SshConnection,
    jumpHostName: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onConnect: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(CardShape)
            .background(Theme[colors][surface])
            .border(1.dp, if (selected) Theme[colors][primary] else Color.Transparent, CardShape)
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UnstyledIcon(
                        imageVector = Icons.Host,
                        contentDescription = null,
                        tint = if (selected) Theme[colors][primary] else Theme[colors][onSurface],
                    )
                    Text(
                        text = connection.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Tooltip(text = "Connect") {
                        IconButton(
                            onClick = onConnect,
                            imageVector = Icons.Connect,
                            contentDescription = "Connect to ${connection.name}",
                            tint = Theme[colors][primary],
                        )
                    }
                }
                Text(
                    text = connection.address,
                    color = Theme[colors][textSecondary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Badge(connection.auth.method.label)
                connection.options.tags.take(MAX_CARD_TAGS).forEach { Badge(it) }
                if (jumpHostName != null) {
                    Text(
                        text = "via $jumpHostName",
                        color = Theme[colors][textSecondary],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
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
