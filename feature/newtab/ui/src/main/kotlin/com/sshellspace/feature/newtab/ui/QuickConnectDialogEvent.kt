package com.sshellspace.feature.newtab.ui

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.designsystem.dialog.DialogEvent
import com.sshellspace.feature.newtab.ui.screen.QuickConnectPalette
import org.koin.compose.viewmodel.koinViewModel

object QuickConnectDialogEvent : DialogEvent() {
    @Composable
    override fun content() {
        QuickConnectPalette(
            viewModel = koinViewModel(),
            onClose = onCloseDialog,
        )
    }
}
