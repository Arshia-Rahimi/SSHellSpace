package com.sshellspace.feature.terminal.ui.screen.emulators

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Terminal {
    fun clear()

    /** Types [text] at the prompt, pressing Enter after it when [pressEnter] is set. */
    fun sendText(text: String, pressEnter: Boolean)
    @Composable fun content(modifier: Modifier)
}
