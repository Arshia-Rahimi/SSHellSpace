package com.sshellspace.feature.terminal.ui.screen.emulators

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Terminal {
    fun clear()
    @Composable fun content(modifier: Modifier)
}
