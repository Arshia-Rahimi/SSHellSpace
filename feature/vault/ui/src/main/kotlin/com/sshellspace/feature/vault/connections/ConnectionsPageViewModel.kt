package com.sshellspace.feature.vault.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.emit
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.vault.connections.components.DeleteConnectionDialogEvent
import com.sshellspace.feature.vault.domain.SaveSshConnectionResult
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import com.sshellspace.feature.vault.domain.SshKeyRepository
import com.sshellspace.feature.terminal.domain.models.SshTerminalParams
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionsPageViewModel(
    private val connectionRepository: SshConnectionRepository,
    sshKeyRepository: SshKeyRepository,
    private val tabManager: TabManager,
) : ViewModel() {
    private val connections = connectionRepository.getConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    private val keys = sshKeyRepository.getKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val searchQuery = MutableStateFlow("")
    private val sidebar = MutableStateFlow<ConnectionSidebarState?>(null)
    private val isSaveInFlight = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val data = combine(connections, keys, searchQuery) { connections, keys, query ->
        ConnectionsPageUiState(connections = connections, keys = keys, searchQuery = query)
    }

    val uiState = combine(data, sidebar, isSaveInFlight, errorMessage) { data, sidebar, inFlight, error ->
        data.copy(sidebar = sidebar, isSaveInFlight = inFlight, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ConnectionsPageUiState())

    fun execute(action: ConnectionsPageAction) {
        when (action) {
            is ConnectionsPageAction.SetSearch -> searchQuery.update { action.value }
            ConnectionsPageAction.OpenCreate -> openSidebar(ConnectionSidebarState(editingId = null, form = ConnectionForm()))
            is ConnectionsPageAction.Select -> openSidebar(
                ConnectionSidebarState(editingId = action.connection.id, form = action.connection.toForm()),
            )
            ConnectionsPageAction.CloseSidebar -> closeSidebar()
            is ConnectionsPageAction.EditForm -> updateSidebar { copy(form = action.update(form)) }
            ConnectionsPageAction.ToggleAdvanced -> updateSidebar { copy(showAdvanced = !showAdvanced) }
            ConnectionsPageAction.ToggleSecretVisible -> updateSidebar { copy(secretVisible = !secretVisible) }
            is ConnectionsPageAction.Connect -> connect(action.connection)
            ConnectionsPageAction.Save -> save(connectAfter = false)
            ConnectionsPageAction.SaveAndConnect -> save(connectAfter = true)
            ConnectionsPageAction.Duplicate -> duplicate()
            ConnectionsPageAction.RequestDelete -> requestDelete()
        }
    }

    private fun openSidebar(state: ConnectionSidebarState) {
        errorMessage.update { null }
        sidebar.update { state }
    }

    private fun closeSidebar() {
        errorMessage.update { null }
        sidebar.update { null }
    }

    private fun updateSidebar(transform: ConnectionSidebarState.() -> ConnectionSidebarState) {
        sidebar.update { it?.transform() }
    }

    private fun connect(connection: SshConnection) {
        tabManager.createNewTab(SshTerminalParams(connectionId = connection.id, title = connection.name))
    }

    private fun save(connectAfter: Boolean) {
        val state = sidebar.value ?: return
        val withRequired = state.copy(showRequiredErrors = true)
        val errors = withRequired.form.validate(connections.value, state.editingId, showRequired = true)
        if (errors.hasErrors) {
            sidebar.update { withRequired.copy(showAdvanced = state.showAdvanced || errors.hasAdvancedErrors) }
            return
        }

        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = connectionRepository.save(state.editingId, state.form.toDraft())) {
                is SaveSshConnectionResult.Success -> {
                    closeSidebar()
                    if (connectAfter) connect(result.connection)
                }
                is SaveSshConnectionResult.Error -> errorMessage.update { result.message }
            }
            isSaveInFlight.update { false }
        }
    }

    /** Opens a new, unsaved connection prefilled from the open one. */
    private fun duplicate() {
        val state = sidebar.value ?: return
        openSidebar(
            ConnectionSidebarState(
                editingId = null,
                form = state.form.copy(name = copyName(state.form.name.trim())),
                showAdvanced = state.showAdvanced,
            ),
        )
    }

    private fun copyName(name: String): String {
        val taken = connections.value.map { it.name.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) "$name copy" else "$name copy $it" }
            .first { it.lowercase() !in taken }
    }

    private fun requestDelete() {
        val id = sidebar.value?.editingId ?: return
        val connection = connections.value.firstOrNull { it.id == id } ?: return
        DeleteConnectionDialogEvent(
            connectionName = connection.name,
            dependentNames = connections.value.filter { it.options.jumpHostId == id }.map(SshConnection::name),
            onConfirm = { delete(id) },
        ).emit()
    }

    private fun delete(id: Uuid) {
        isSaveInFlight.update { true }
        viewModelScope.launch {
            connectionRepository.delete(id)
            isSaveInFlight.update { false }
            if (sidebar.value?.editingId == id) closeSidebar()
        }
    }
}
