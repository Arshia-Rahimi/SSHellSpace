package com.sshellspace.core.ui.designsystem.dialog

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
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
import com.composeunstyled.UnstyledDialog
import com.sshellspace.core.domain.EventBus
import kotlinx.coroutines.flow.filterIsInstance

@Composable
fun AppDialog() {
    var currentDialogEvent by remember { mutableStateOf<DialogEvent?>(null) }

    LaunchedEffect(Unit) {
        EventBus.events
            .filterIsInstance<DialogEvent>()
            .collect {
                if (currentDialogEvent == null) {
                    currentDialogEvent = it
                    currentDialogEvent?.setOnCloseDialog {
                        currentDialogEvent = null
                    }
                }
            }
    }

    UnstyledDialog(
        visible = currentDialogEvent != null,
        onDismissRequest = { currentDialogEvent = null },
        overlay = {
            Scrim(scrimColor = Color.Black.copy(0.3f), enter = fadeIn(), exit = fadeOut())
        },
    ) {
        currentDialogEvent?.content()
    }
}
