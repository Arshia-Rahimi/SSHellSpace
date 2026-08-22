package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing

abstract class TabViewModel(
    val id: TabId = generateTabId(),
    initialTitle: String,
    initialIcon: ImageVector?
) {
    var title by mutableStateOf(initialTitle)
    var icon by mutableStateOf(initialIcon)

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing.immediate)

    open fun onClose() { scope.cancel() }

    @Composable
    abstract fun screen()
}

@OptIn(ExperimentalUuidApi::class)
private fun generateTabId() = Uuid.generateV7().toString()
