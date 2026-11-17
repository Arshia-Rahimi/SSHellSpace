package com.sshellspace.feature.terminal.ui.screen.emulators

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NoTitle: StateFlow<String?> = MutableStateFlow(null)

interface Terminal {
    fun clear()

    /** Types [text] at the prompt, pressing Enter after it when [pressEnter] is set. */
    fun sendText(text: String, pressEnter: Boolean)
    @Composable fun content(modifier: Modifier)

    /** The title the program in the terminal set, such as vim's file name. Null while it has set none. */
    val title: StateFlow<String?> get() = NoTitle
}
