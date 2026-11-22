package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.feature.terminal.domain.models.Shell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class NewTabChoicesTest {
    private val bash = Shell("bash", "/bin/bash")
    private val zsh = Shell("zsh", "/bin/zsh")
    private val web = SshConnection(
        Uuid.random(),
        "web-1",
        "web.example.com",
        port = 2222,
        username = "root",
        auth = SshConnectionAuth.Agent,
    )

    @Test
    fun `choices follow the dialog order, with the shortcut on the default shell`() {
        val choices = NewTabDialogState(
            defaultShell = bash,
            otherShells = listOf(zsh),
            connections = listOf(web),
            newTabShortcutLabel = "⌘T",
            isLoading = false,
        ).choices()

        assertEquals(listOf("bash", "web-1", "This device", "zsh"), choices.map { it.title })
        assertEquals(
            listOf("Default", "Connections", "Files", "Other shells"),
            choices.map { it.section },
        )
        assertEquals("⌘T", choices.first().trailing)
        assertNull(choices[1].trailing)
        assertEquals("root@web.example.com:2222", choices[1].subtitle)
        assertEquals(NewTabDialogAction.CreateNewTab(bash), choices[0].action)
        assertEquals(NewTabDialogAction.Connect(web), choices[1].action)
        assertEquals(NewTabDialogAction.BrowseFiles(web), choices[1].secondaryAction)
        assertEquals(NewTabDialogAction.BrowseThisDevice, choices[2].action)
    }

    @Test
    fun `while shells are discovered, this device is still selectable`() {
        val choices = NewTabDialogState(isLoading = true).choices()

        assertEquals(listOf("This device"), choices.map { it.title })
        assertEquals(listOf("Files"), choices.map { it.section })
    }

    @Test
    fun `arrow keys wrap around the rows`() {
        assertEquals(0, moveSelection(3, 1, 4))
        assertEquals(3, moveSelection(0, -1, 4))
        assertEquals(2, moveSelection(1, 1, 4))
        assertEquals(0, moveSelection(0, 1, 1))
        assertEquals(0, moveSelection(0, 1, 0))
    }
}
