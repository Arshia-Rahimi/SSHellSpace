package com.github.arshia_rahimi.sshellspace.core.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing

abstract class TabViewModel(
    override val id: TabId = generateTabId(),
    initialTitle: String,
    initialIcon: ImageVector?
): Tab {
    override var title by mutableStateOf(initialTitle)
    override var icon by mutableStateOf(initialIcon)

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing.immediate)

    open fun onClose() { scope.cancel() }

    @Composable
    abstract fun screen()
}
