package com.sshellspace.newtab

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.TerminalTabInstance
import org.koin.compose.koinInject

// todo: this should be one general dialog for the whole app with the controller
@Composable
fun NewTabDialog() {
    val tabController = koinInject<TabController>()
    var shouldShowNewTabDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        NewTabController.newTabButtonEvent
            .collect { shouldShowNewTabDialog = true }
    }

    UnstyledDialog(
        visible = shouldShowNewTabDialog,
        onDismissRequest = { shouldShowNewTabDialog = false },
        overlay = {
            Scrim(scrimColor = Color.Black.copy(0.3f), enter = fadeIn(), exit = fadeOut())
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DialogPanel(
                modifier = Modifier
            ) {
                Column(
                    modifier = Modifier.background(Theme[colors][background]),
                ) {
                    Button(
                        onClick = {
                            tabController.createNewTab(TerminalTabInstance("zsh"))
                            shouldShowNewTabDialog = false
                        },
                    ) {
                        Text("new terminal tab")
                    }
                }
            }
        }
    }
}
