package com.sshellspace.feature.vault.domain

import com.sshellspace.core.domain.models.Snippet
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface SnippetRepository {
    fun getSnippets(): Flow<List<Snippet>>

    /** Creates a snippet when [id] is null, otherwise replaces the snippet with that id. */
    suspend fun save(id: Uuid?, name: String, command: String, runImmediately: Boolean): SaveSnippetResult
    suspend fun delete(id: Uuid)
}

sealed interface SaveSnippetResult {
    data object Success : SaveSnippetResult
    data class Error(val message: String) : SaveSnippetResult
}
