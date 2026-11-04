package com.sshellspace.feature.vault.connections

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ConnectionsOrganizingTest {
    private fun connection(name: String, group: String? = null, tags: List<String> = emptyList()) = SshConnection(
        Uuid.random(),
        name,
        "host",
        username = "root",
        auth = SshConnectionAuth.Agent,
        options = SshConnectionOptions(group = group, tags = tags),
    )

    private val state = ConnectionsPageUiState(
        connections = listOf(
            connection("loose"),
            connection("z-web", group = "Staging", tags = listOf("web")),
            connection("a-db", group = "production", tags = listOf("db", "web")),
        ),
    )

    @Test
    fun `groups are sorted, with ungrouped hosts last`() {
        assertEquals(listOf("production", "Staging", null), state.sections.map { it.name })
    }

    @Test
    fun `a list with no groups is one unnamed section`() {
        val plain = ConnectionsPageUiState(connections = listOf(connection("a"), connection("b")))

        assertEquals(listOf<String?>(null), plain.sections.map { it.name })
    }

    @Test
    fun `tags are listed once each, sorted`() {
        assertEquals(listOf("db", "web"), state.allTags)
    }

    @Test
    fun `search covers groups and tags`() {
        assertEquals(listOf("a-db"), state.copy(searchQuery = "PRODUCTION").filteredConnections.map { it.name })
        assertEquals(listOf("a-db"), state.copy(searchQuery = "db").filteredConnections.map { it.name })
    }

    @Test
    fun `a tag filter combines with search`() {
        val tagged = state.copy(selectedTag = "web")

        assertEquals(listOf("z-web", "a-db"), tagged.filteredConnections.map { it.name })
        assertEquals(listOf("a-db"), tagged.copy(searchQuery = "a-").filteredConnections.map { it.name })
    }

    @Test
    fun `the form tidies tags and an empty group`() {
        val draft = ConnectionForm(
            name = "n",
            host = "h",
            username = "u",
            group = "  ",
            tags = " web, ,db, web ",
        ).toDraft()

        assertEquals(null, draft.options.group)
        assertEquals(listOf("web", "db"), draft.options.tags)
    }

    @Test
    fun `group and tags survive editing`() {
        val form = connection("n", group = "Prod", tags = listOf("a", "b")).toForm()

        assertEquals("Prod", form.group)
        assertEquals("a, b", form.tags)
    }
}
