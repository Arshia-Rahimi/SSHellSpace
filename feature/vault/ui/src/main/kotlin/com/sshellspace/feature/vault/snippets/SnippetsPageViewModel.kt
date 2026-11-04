package com.sshellspace.feature.vault.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.Snippet
import com.sshellspace.feature.vault.domain.SaveSnippetResult
import com.sshellspace.feature.vault.domain.SnippetRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SnippetForm(
    /** Null while creating a new snippet. */
    val editingId: Uuid? = null,
    val name: String = "",
    val command: String = "",
    val runImmediately: Boolean = false,
)

data class SnippetsPageUiState(
    val snippets: List<Snippet> = emptyList(),
    val searchQuery: String = "",
    val form: SnippetForm? = null,
    val errorMessage: String? = null,
) {
    val filteredSnippets: List<Snippet>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return snippets
            return snippets.filter {
                it.name.contains(query, ignoreCase = true) || it.command.contains(query, ignoreCase = true)
            }
        }
}

sealed interface SnippetsPageAction {
    data class SetSearch(val value: String) : SnippetsPageAction
    data object OpenCreate : SnippetsPageAction
    data class Select(val snippet: Snippet) : SnippetsPageAction
    data object CloseForm : SnippetsPageAction
    data class EditForm(val update: (SnippetForm) -> SnippetForm) : SnippetsPageAction
    data object Save : SnippetsPageAction
    data object Delete : SnippetsPageAction
}

class SnippetsPageViewModel(
    private val repository: SnippetRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val form = MutableStateFlow<SnippetForm?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(repository.getSnippets(), searchQuery, form, errorMessage) { snippets, query, form, error ->
        SnippetsPageUiState(snippets, query, form, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SnippetsPageUiState())

    fun execute(action: SnippetsPageAction) {
        when (action) {
            is SnippetsPageAction.SetSearch -> searchQuery.update { action.value }
            SnippetsPageAction.OpenCreate -> openForm(SnippetForm())
            is SnippetsPageAction.Select -> openForm(
                SnippetForm(action.snippet.id, action.snippet.name, action.snippet.command, action.snippet.runImmediately),
            )
            SnippetsPageAction.CloseForm -> openForm(null)
            is SnippetsPageAction.EditForm -> form.update { it?.let(action.update) }
            SnippetsPageAction.Save -> save()
            SnippetsPageAction.Delete -> delete()
        }
    }

    private fun openForm(value: SnippetForm?) {
        errorMessage.update { null }
        form.update { value }
    }

    private fun save() {
        val current = form.value ?: return
        viewModelScope.launch {
            when (val result = repository.save(current.editingId, current.name, current.command, current.runImmediately)) {
                SaveSnippetResult.Success -> openForm(null)
                is SaveSnippetResult.Error -> errorMessage.update { result.message }
            }
        }
    }

    private fun delete() {
        val id = form.value?.editingId ?: return
        viewModelScope.launch {
            repository.delete(id)
            openForm(null)
        }
    }
}
