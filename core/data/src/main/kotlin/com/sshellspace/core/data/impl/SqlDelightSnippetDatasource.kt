package com.sshellspace.core.data.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sshellspace.core.data.SnippetDatasource
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Snippets
import com.sshellspace.core.domain.models.Snippet
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightSnippetDatasource(
    private val db: Database,
) : SnippetDatasource {
    override fun index(): Flow<List<Snippet>> =
        db.snippetQueries.index().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { Snippet(Uuid.parse(it.id), it.name, it.command, it.run_immediately != 0L) } }

    override suspend fun upsert(snippet: Snippet) {
        withContext(Dispatchers.IO) {
            db.snippetQueries.upsert(
                Snippets(
                    id = snippet.id.toString(),
                    name = snippet.name,
                    command = snippet.command,
                    run_immediately = if (snippet.runImmediately) 1L else 0L,
                ),
            )
        }
    }

    override suspend fun delete(id: Uuid) {
        withContext(Dispatchers.IO) { db.snippetQueries.delete(id.toString()) }
    }
}
