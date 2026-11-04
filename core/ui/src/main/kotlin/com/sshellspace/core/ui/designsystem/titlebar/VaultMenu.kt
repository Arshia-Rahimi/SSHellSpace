package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import dev.nucleusframework.window.noWindowDrag
import sshellspace.core.ui.generated.resources.Res
import sshellspace.core.ui.generated.resources.exit_app
import sshellspace.core.ui.generated.resources.lock_vault

/** The dropdown on the vault's tab, to lock the vault or exit the app. */
@Composable
internal fun VaultMenu(
    vaultName: String,
    onLock: () -> Unit,
    onExit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        alignment = AnchorAlignment.Start,
        sideOffset = 4.dp,
        anchor = {
            IconButton(
                onClick = { expanded = !expanded },
                imageVector = Icons.ChevronDown,
                contentDescription = "Options for $vaultName",
                tint = Theme[colors][textSecondary],
                modifier = Modifier.noWindowDrag(),
            )
        },
        panel = {
            MenuPanel(width = 160.dp) {
                MenuItem(onClick = onLock) {
                    Text(Res.string.lock_vault.asString())
                }
                MenuItem(onClick = onExit) {
                    Text(Res.string.exit_app.asString())
                }
            }
        },
    )
}
