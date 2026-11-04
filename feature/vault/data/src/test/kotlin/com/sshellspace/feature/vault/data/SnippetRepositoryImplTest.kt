package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.SnippetDatasource
import com.sshellspace.core.domain.models.Snippet
import com.sshellspace.feature.vault.domain.SaveSnippetResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class SnippetRepositoryImplTest {

    private class FakeDatasource : SnippetDatasource {
        val stored = MutableStateFlow(emptyList<Snippet>())
        override fun index() = stored
        override suspend fun upsert(snippet: Snippet) {
            stored.value = stored.value.filterNot { it.id == snippet.id } + snippet
        }

        override suspend fun delete(id: Uuid) {
            stored.value = stored.value.filterNot { it.id == id }
        }
    }

    private val datasource = FakeDatasource()
    private val repository = SnippetRepositoryImpl(datasource)

    @Test
    fun `saves a trimmed snippet`() = runTest {
        assertEquals(SaveSnippetResult.Success, repository.save(null, " logs ", " tail -f x \n", true))

        val saved = datasource.stored.value.single()
        assertEquals("logs", saved.name)
        assertEquals("tail -f x", saved.command)
        assertEquals(true, saved.runImmediately)
    }

    @Test
    fun `needs a name and a command`() = runTest {
        assertIs<SaveSnippetResult.Error>(repository.save(null, " ", "ls", false))
        assertIs<SaveSnippetResult.Error>(repository.save(null, "n", " ", false))
    }

    @Test
    fun `names are unique, except when editing the same snippet`() = runTest {
        repository.save(null, "logs", "a", false)
        val id = datasource.stored.value.single().id

        assertIs<SaveSnippetResult.Error>(repository.save(null, "LOGS", "b", false))
        assertEquals(SaveSnippetResult.Success, repository.save(id, "logs", "c", false))
        assertEquals("c", datasource.stored.value.single().command)
    }
}
