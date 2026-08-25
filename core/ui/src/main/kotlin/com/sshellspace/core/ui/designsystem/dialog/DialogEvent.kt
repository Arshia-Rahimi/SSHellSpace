package com.sshellspace.core.ui.designsystem.dialog

import androidx.compose.runtime.Composable
import com.sshellspace.core.domain.Event

abstract class DialogEvent: Event {
    protected var onCloseDialog: (() -> Unit)? = null

    @JvmName("setOnCloseDialogCallback")
    fun setOnCloseDialog(callback: () -> Unit) {
        onCloseDialog = callback
    }

    @Composable abstract fun content()
}
