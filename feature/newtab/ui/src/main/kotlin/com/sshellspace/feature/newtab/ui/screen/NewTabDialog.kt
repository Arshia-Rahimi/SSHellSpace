package com.sshellspace.feature.newtab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.models.Shell

@Composable
fun NewTabDialog(
    viewModel: NewTabDialogViewModel,
    onCloseDialog: (() -> Unit)?,
) {
    val uiState by viewModel.uiState.collectAsState()

    NewTabDialogContent(
        uiState = uiState,
        onSelect = { shell ->
            viewModel.execute(NewTabDialogAction.CreateNewTab(shell))
            onCloseDialog?.invoke()
        },
    )
}

@Composable
private fun NewTabDialogContent(
    uiState: NewTabDialogState,
    onSelect: (Shell) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 480.dp)
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], shape)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                uiState.isLoading -> SectionLabel("Looking for shells…")
                uiState.defaultShell == null -> SectionLabel("No shells found")
                else -> {
                    SectionLabel("Default")
                    ShellRow(
                        shell = uiState.defaultShell,
                        highlighted = true,
                        trailing = uiState.newTabShortcutLabel,
                        onClick = { onSelect(uiState.defaultShell) },
                    )
                    if (uiState.otherShells.isNotEmpty()) {
                        UnstyledHorizontalSeparator(
                            color = Theme[colors][separator],
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        SectionLabel("Other shells")
                        uiState.otherShells.forEach { shell ->
                            ShellRow(
                                shell = shell,
                                highlighted = false,
                                trailing = null,
                                onClick = { onSelect(shell) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Theme[colors][textSecondary],
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ShellRow(
    shell: Shell,
    highlighted: Boolean,
    trailing: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (highlighted) Theme[colors][primary] else Color.Transparent, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(shell.name)
                shell.command?.let { Text(it, color = Theme[colors][textSecondary]) }
            }
            if (trailing != null) {
                Text(trailing, color = Theme[colors][textSecondary])
            }
        }
    }
}
