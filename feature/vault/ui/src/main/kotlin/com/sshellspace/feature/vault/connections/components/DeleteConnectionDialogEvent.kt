package com.sshellspace.feature.vault.connections.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.dialog.DialogEvent
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary

class DeleteConnectionDialogEvent(
    private val connectionName: String,
    /** Connections that use this one as their jump host. */
    private val dependentNames: List<String>,
    private val onConfirm: () -> Unit,
) : DialogEvent() {
    @Composable
    override fun content() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Theme[colors][surfaceElevated])
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Delete $connectionName?")
                if (dependentNames.isNotEmpty()) {
                    Text(
                        text = "It's the jump host for ${dependentNames.joinToString()}. " +
                            "They will connect directly instead.",
                        color = Theme[colors][textSecondary],
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onCloseDialog?.invoke() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onConfirm()
                            onCloseDialog?.invoke()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}
