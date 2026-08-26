package com.sshellspace.core.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class Tab(
    val type: TabType,
    val id: TabId = generateTabId(),
    initialTitle: String,
    initialIcon: ImageVector?
) {
    val storeOwner = TabViewModelStoreOwner()

    var title by mutableStateOf(initialTitle)
    var icon by mutableStateOf(initialIcon)

    @Composable abstract fun screen()
}

class TabViewModelStoreOwner: ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@OptIn(ExperimentalUuidApi::class)
private fun generateTabId() = Uuid.generateV7().toString()
