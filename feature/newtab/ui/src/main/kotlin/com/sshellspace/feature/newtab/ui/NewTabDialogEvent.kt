package com.sshellspace.feature.newtab.ui

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.designsystem.dialog.DialogEvent
import com.sshellspace.feature.newtab.ui.screen.NewTabDialog
import org.koin.compose.viewmodel.koinViewModel

object NewTabDialogEvent : DialogEvent() {
    @Composable
    override fun content() {
        NewTabDialog(
            viewModel = koinViewModel(),
            onCloseDialog = onCloseDialog,
        )
    }
}
