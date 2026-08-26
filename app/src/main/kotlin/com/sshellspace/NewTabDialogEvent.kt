package com.sshellspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.dialog.DialogEvent
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.Shell
import com.sshellspace.feature.terminal.ui.TerminalTab
import org.koin.compose.koinInject

object NewTabDialogEvent : DialogEvent() {
    @Composable
    override fun content() {
        val tabController = koinInject<TabController>()

        Column(
            modifier = Modifier.background(Theme[colors][background]),
        ) {
            Button(
                onClick = {
                    tabController.createNewTab(TerminalTab(Shell("zsh", "/bin/zsh")))
                    onCloseDialog?.invoke()
                },
            ) {
                Text("new terminal tab")
            }
        }
    }
}
