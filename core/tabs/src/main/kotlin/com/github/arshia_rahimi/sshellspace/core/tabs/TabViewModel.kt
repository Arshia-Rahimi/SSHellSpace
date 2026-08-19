package com.github.arshia_rahimi.sshellspace.core.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel

abstract class TabViewModel(
    override val id: TabId = generateTabId(),
    initialTitle: String,
    initialIcon: ImageVector?
) : ViewModel(), Tab {
    override var title by mutableStateOf(initialTitle)
    override var icon by mutableStateOf(initialIcon)

    @Composable
    abstract fun screen()
}
