package com.sshellspace.feature.newtab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors

@Composable
fun NewTabDialog(
    onCloseDialog: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.background(Theme[colors][background]),
    ) {
        // todo
    }
}
