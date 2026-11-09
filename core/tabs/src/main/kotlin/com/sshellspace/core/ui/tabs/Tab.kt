package com.sshellspace.core.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.sshellspace.core.domain.models.TabParams
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
    tabId: TabId = generateTabId(),
    initialIcon: ImageVector? = null,
    /**
     * Keeps the content composed, though hidden, while another tab is on show, once it has been shown. For content that
     * is costly or disruptive to rebuild, like a terminal, which would resize and make the shell redraw.
     */
    val keepAlive: Boolean = false,
) : TabController {
    val tab: Tab = Tab(tabId)

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

    private var _textInput: ((String, Boolean) -> Unit)? by mutableStateOf(null)
    val acceptsText: Boolean get() = _textInput != null

    override fun setTextInput(input: ((String, Boolean) -> Unit)?) {
        this._textInput = input
    }

    fun sendText(text: String, pressEnter: Boolean) {
        _textInput?.invoke(text, pressEnter)
    }

    val content: @Composable () -> Unit = { contentProvider(this) }

    override fun equals(other: Any?) = other is TabItem && other.tab.id == tab.id
    override fun hashCode() = tab.id.hashCode()
}

fun interface TabItemFactory<T: TabParams> {
    fun create(tabParameters: T): TabItem
}

@OptIn(ExperimentalUuidApi::class)
private fun generateTabId() = Uuid.generateV7().toString()
