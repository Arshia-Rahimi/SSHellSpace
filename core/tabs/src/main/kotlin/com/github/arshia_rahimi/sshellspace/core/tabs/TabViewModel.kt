package com.github.arshia_rahimi.sshellspace.core.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class TabViewModel(
    override val id: TabId = generateTabId(),
    initialTitle: String,
    initialIcon: ImageVector?
) : TabHandle {
    override var title by mutableStateOf(initialTitle)
    override var icon by mutableStateOf(initialIcon)

    @Composable
    abstract fun screen()
}

