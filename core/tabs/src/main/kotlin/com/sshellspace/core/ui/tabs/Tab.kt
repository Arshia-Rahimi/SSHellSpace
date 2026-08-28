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

typealias TabId = String

class Tab(
    val id: TabId = generateTabId(),
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

interface TabType

class TabItem(
    val type: TabType,
    contentProvider: @Composable (TabItem) -> Unit,
    initialTitle: String,
    val tab: Tab = Tab(),
    initialIcon: ImageVector? = null,
) : TabController {
    private var _title: String by mutableStateOf(initialTitle)
    val title: String get() = _title

    private var _icon: ImageVector? by mutableStateOf(initialIcon)
    val icon: ImageVector? get() = _icon

    override fun setTitle(title: String) {
        this._title = title
    }

    override fun setIcon(icon: ImageVector?) {
        this._icon = icon
    }

    val content: @Composable () -> Unit = { contentProvider(this) }

    override fun equals(other: Any?) = other is TabItem && other.tab.id == tab.id
    override fun hashCode() = tab.id.hashCode()
}

fun interface TabInitializer {
    fun create(): TabItem
}

@OptIn(ExperimentalUuidApi::class)
private fun generateTabId() = Uuid.generateV7().toString()
