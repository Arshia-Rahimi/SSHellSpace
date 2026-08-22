package com.github.arshia_rahimi.sshellspace.newtab

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDialog
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabController
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalTabInstance
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
    ) {
        Column {
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
