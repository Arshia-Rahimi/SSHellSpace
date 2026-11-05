package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.windowDragArea

/** The title bar while no vault is unlocked: window controls only, since there are no tabs to show. */
@Composable
fun DecoratedWindowScope.LockedTitleBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            .windowDragArea(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowControls()
    }
}
