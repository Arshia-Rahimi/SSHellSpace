package com.sshellspace.core.ui.tabs

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the tab this content belongs to is the one on show. A [TabItem.keepAlive] tab stays composed while hidden,
 * so its content must not take focus while this is false.
 */
val LocalTabActive = compositionLocalOf { true }
