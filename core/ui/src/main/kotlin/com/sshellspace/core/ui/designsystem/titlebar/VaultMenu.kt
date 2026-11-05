package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import dev.nucleusframework.window.noWindowDrag
import sshellspace.core.ui.generated.resources.Res
import sshellspace.core.ui.generated.resources.exit_app
import sshellspace.core.ui.generated.resources.lock_vault

private val MenuWidth = 230.dp

/** The dropdown on the vault's tab, to lock the vault or exit the app. */
@Composable
internal fun VaultMenu(
    vaultName: String,
    lockShortcut: String?,
    onLock: () -> Unit,
    onExit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        alignment = AnchorAlignment.Start,
        sideOffset = 6.dp,
        anchor = {
            TitleBarIconButton(
                icon = Icons.ChevronDown,
                contentDescription = "Options for $vaultName",
                onClick = { expanded = !expanded },
                size = 18.dp,
                iconSize = 12.dp,
                modifier = Modifier.noWindowDrag(),
            )
        },
        panel = {
            MenuPanel(width = MenuWidth, onDismiss = { expanded = false }) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(vaultName, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Vault unlocked", color = Theme[colors][textSecondary], fontSize = 12.sp)
                }
                MenuDivider()
                VaultMenuItem(Icons.Vault, Res.string.lock_vault.asString(), lockShortcut, onLock)
                MenuDivider()
                VaultMenuItem(Icons.Exit, Res.string.exit_app.asString(), null, onExit)
            }
        },
    )
}

@Composable
private fun VaultMenuItem(icon: ImageVector, label: String, shortcut: String?, onClick: () -> Unit) {
    MenuItem(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
    ) {
        UnstyledIcon(
            imageVector = icon,
            contentDescription = null,
            tint = Theme[colors][textSecondary],
            modifier = Modifier.size(14.dp),
        )
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        shortcut?.let { Text(it, color = Theme[colors][textSecondary], fontSize = 12.sp) }
    }
}

@Composable
private fun MenuDivider() {
    UnstyledHorizontalSeparator(
        color = Theme[colors][separator],
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
