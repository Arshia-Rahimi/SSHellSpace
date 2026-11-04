package com.sshellspace.core.data

import com.sshellspace.core.domain.models.Snippet
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface SnippetDatasource {
    fun index(): Flow<List<Snippet>>

    /** Inserts [snippet], or replaces the stored one with the same id. */
    suspend fun upsert(snippet: Snippet)
    suspend fun delete(id: Uuid)
}
