package com.github.arshia_rahimi.sshellspace.newtab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.arshia_rahimi.core.ui.designsystem.components.Text
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabController
import com.github.arshia_rahimi.sshellspace.feature.terminal.TerminalTabInstance
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.material.MaterialDecoratedDialog
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

    if (shouldShowNewTabDialog) {
        Dialog(
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
}
