package com.sshellspace.feature.newtab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors

@Composable
fun NewTabDialog(
    viewModel: NewTabDialogViewModel,
    onCloseDialog: (() -> Unit)?,
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .background(Theme[colors][background])
                .width(100.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            uiState.discoveredShells.forEach { shell ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.execute(NewTabDialogAction.CreateNewTab(shell))
                        onCloseDialog?.invoke()
                    },
                ) {
                    Text(shell.name)
                }
            }
        }
    }
}
