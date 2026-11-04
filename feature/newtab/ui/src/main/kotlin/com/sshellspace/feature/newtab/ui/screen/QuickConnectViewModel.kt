package com.sshellspace.feature.newtab.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.core.domain.models.Snippet
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import com.sshellspace.feature.vault.domain.SnippetRepository
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface PaletteItem {
    val key: String
    val title: String
    val subtitle: String
    val actionLabel: String
    val searchText: String

    data class Host(val connection: SshConnection) : PaletteItem {
        override val key = "host:${connection.id}"
        override val title = connection.name
        override val subtitle = buildString {
            append(connection.username).append('@').append(connection.host)
            if (connection.port != DEFAULT_SSH_PORT) append(':').append(connection.port)
            connection.options.group?.let { append("  ·  ").append(it) }
        }
        override val actionLabel = "Connect"
        override val searchText =
            listOfNotNull(title, subtitle, connection.options.group).plus(connection.options.tags).joinToString(" ")
    }

    data class SnippetItem(val snippet: Snippet) : PaletteItem {
        override val key = "snippet:${snippet.id}"
        override val title = snippet.name
        // Shows that there's more, since a snippet can run as soon as it's chosen.
        override val subtitle = snippet.command.lines().let { lines ->
            if (lines.size > 1) "${lines.first()}  (+${lines.size - 1} more lines)" else lines.first()
        }
        override val actionLabel = if (snippet.runImmediately) "Run" else "Insert"
        override val searchText = "$title ${snippet.command}"
    }
}

data class QuickConnectUiState(
    val connections: List<SshConnection> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    /** Snippets go to the terminal in the current tab, so they need one. */
    val canSendToTerminal: Boolean = false,
) {
    fun canChoose(item: PaletteItem) = item is PaletteItem.Host || canSendToTerminal

    /** Every word has to appear somewhere, in any order. Name matches rank first, then hosts before snippets. */
    fun search(query: String): List<PaletteItem> {
        val all = connections.map(PaletteItem::Host) + snippets.map(PaletteItem::SnippetItem)
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all
            .filter { item -> words.all { item.searchText.contains(it, ignoreCase = true) } }
            .sortedBy { item -> if (words.all { item.title.contains(it, ignoreCase = true) }) 0 else 1 }
    }
}

sealed interface QuickConnectAction {
    data class Choose(val item: PaletteItem) : QuickConnectAction
}

class QuickConnectViewModel(
    private val tabManager: TabManager,
    connectionRepository: SshConnectionRepository,
    snippetRepository: SnippetRepository,
) : ViewModel() {
    val uiState = combine(
        connectionRepository.getConnections(),
        snippetRepository.getSnippets(),
        tabManager.tabsState(),
    ) { connections, snippets, tabs ->
        QuickConnectUiState(connections, snippets, tabs.currentTab.acceptsText)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), QuickConnectUiState())

    fun execute(action: QuickConnectAction) {
        when (action) {
            is QuickConnectAction.Choose -> when (val item = action.item) {
                is PaletteItem.Host -> tabManager.createNewTab(
                    SshTerminalParams(connectionId = item.connection.id, title = item.connection.name),
                )
                is PaletteItem.SnippetItem -> tabManager.tabsState().value.currentTab
                    .sendText(item.snippet.command, item.snippet.runImmediately)
            }
        }
    }
}
