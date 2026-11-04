package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.core.domain.models.Snippet
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class QuickConnectSearchTest {
    private fun host(name: String, host: String = "h.example.com", group: String? = null, tags: List<String> = emptyList()) =
        SshConnection(
            Uuid.random(),
            name,
            host,
            username = "root",
            auth = SshConnectionAuth.Agent,
            options = SshConnectionOptions(group = group, tags = tags),
        )

    private val web = host("web-1", group = "Production", tags = listOf("nginx"))
    private val db = host("db-1", host = "web.internal")
    private val logs = Snippet(Uuid.random(), "Tail logs", "tail -f /var/log/nginx.log")
    private val state = QuickConnectUiState(listOf(web, db), listOf(logs))

    private fun titles(query: String) = state.search(query).map { it.title }

    @Test
    fun `an empty search lists hosts then snippets`() {
        assertEquals(listOf("web-1", "db-1", "Tail logs"), titles("  "))
    }

    @Test
    fun `searches hosts by group and tag, and snippets by command`() {
        assertEquals(listOf("web-1"), titles("production"))
        assertEquals(listOf("web-1", "Tail logs"), titles("nginx"))
    }

    @Test
    fun `every word has to match, in any order`() {
        assertEquals(listOf("web-1"), titles("nginx production"))
        assertEquals(emptyList(), titles("nginx nothing"))
    }

    @Test
    fun `a match on the name ranks above a match elsewhere`() {
        assertEquals(listOf("web-1", "db-1"), titles("web"))
    }

    @Test
    fun `snippets need a terminal but hosts don't`() {
        val hostItem = PaletteItem.Host(web)
        val snippetItem = PaletteItem.SnippetItem(logs)

        assertTrue(state.canChoose(hostItem))
        assertFalse(state.canChoose(snippetItem))
        assertTrue(state.copy(canSendToTerminal = true).canChoose(snippetItem))
    }

    @Test
    fun `a multi-line snippet says it has more lines`() {
        val item = PaletteItem.SnippetItem(logs.copy(command = "cd /tmp\nrm -rf build\nmake"))

        assertEquals("cd /tmp  (+2 more lines)", item.subtitle)
    }

    @Test
    fun `a snippet says whether it runs straight away`() {
        assertEquals("Insert", PaletteItem.SnippetItem(logs).actionLabel)
        assertEquals("Run", PaletteItem.SnippetItem(logs.copy(runImmediately = true)).actionLabel)
    }
}
