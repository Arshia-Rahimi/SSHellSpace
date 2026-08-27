package com.sshellspace.feature.newtab.ui

import androidx.compose.runtime.Composable
import com.sshellspace.core.ui.designsystem.dialog.DialogEvent
import com.sshellspace.feature.newtab.ui.screen.NewTabDialog

object NewTabDialogEvent : DialogEvent() {
    @Composable
    override fun content() = NewTabDialog(onCloseDialog)
}
