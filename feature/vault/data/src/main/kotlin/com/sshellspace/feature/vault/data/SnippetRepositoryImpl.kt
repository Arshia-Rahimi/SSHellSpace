package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.SnippetDatasource
import com.sshellspace.core.data.generateUuid
import com.sshellspace.core.domain.models.Snippet
import com.sshellspace.feature.vault.domain.SaveSnippetResult
import com.sshellspace.feature.vault.domain.SnippetRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first

class SnippetRepositoryImpl(
    private val datasource: SnippetDatasource,
) : SnippetRepository {
    override fun getSnippets() = datasource.index()

    override suspend fun save(id: Uuid?, name: String, command: String, runImmediately: Boolean): SaveSnippetResult {
        val trimmedName = name.trim()
        val snippetId = id ?: generateUuid()
        return when {
            trimmedName.isEmpty() -> SaveSnippetResult.Error("Name cannot be empty")
            command.isBlank() -> SaveSnippetResult.Error("Command cannot be empty")
            datasource.index().first().any { it.id != snippetId && it.name.equals(trimmedName, ignoreCase = true) } ->
                SaveSnippetResult.Error("$trimmedName already exists")
            else -> {
                datasource.upsert(Snippet(snippetId, trimmedName, command.trim(), runImmediately))
                SaveSnippetResult.Success
            }
        }
    }

    override suspend fun delete(id: Uuid) = datasource.delete(id)
}
