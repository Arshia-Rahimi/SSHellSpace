package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.primaryHovered
import com.sshellspace.core.ui.designsystem.theme.textSecondary

private val UpdateMenuWidth = 250.dp

/**
 * Shown once a new version is downloaded and verified. It opens a menu rather than restarting straight away, since
 * restarting closes whatever is open, and the menu says how much that is.
 */
@Composable
internal fun UpdateButton(
    version: String,
    openSessionCount: () -> Int,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Counted when the menu opens, since tabs can open and close while the button is on show.
    var open by remember { mutableStateOf(0) }

    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        alignment = AnchorAlignment.End,
        sideOffset = 6.dp,
        anchor = {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Button(
                onClick = {
                    open = openSessionCount()
                    expanded = !expanded
                },
                interactionSource = interaction,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Theme[colors][if (hovered) primaryHovered else primary]),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    UnstyledIcon(
                        imageVector = Icons.Download,
                        contentDescription = null,
                        tint = Theme[colors][onPrimary],
                        modifier = Modifier.size(12.dp),
                    )
                    Text("Update", color = Theme[colors][onPrimary], fontSize = 12.sp)
                }
            }
        },
        panel = {
            MenuPanel(width = UpdateMenuWidth, onDismiss = { expanded = false }) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Version $version is ready", fontSize = 13.sp)
                    Text(
                        when (open) {
                            0 -> "The app will restart to install it."
                            1 -> "Restarting closes 1 open tab or port forward."
                            else -> "Restarting closes $open open tabs and port forwards."
                        },
                        color = Theme[colors][textSecondary],
                        fontSize = 12.sp,
                    )
                }
                MenuItem(
                    onClick = {
                        expanded = false
                        onRestart()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    UnstyledIcon(
                        imageVector = Icons.Refresh,
                        contentDescription = null,
                        tint = Theme[colors][textSecondary],
                        modifier = Modifier.size(14.dp),
                    )
                    Text("Restart and update", fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        },
    )
}
