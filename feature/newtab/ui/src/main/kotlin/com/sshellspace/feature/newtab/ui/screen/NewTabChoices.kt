package com.sshellspace.feature.newtab.ui.screen

import com.sshellspace.core.domain.models.DEFAULT_SSH_PORT
import com.sshellspace.core.domain.models.SshConnection

/** A row of the new tab dialog, in the order the arrow keys walk it. */
internal data class NewTabChoice(
    val key: String,
    /** Set on the first row of a section; the dialog draws the heading before it. */
    val section: String? = null,
    val title: String,
    val subtitle: String?,
    val trailing: String? = null,
    val action: NewTabDialogAction,
    /** The folder button on a connection row, so it stays available alongside the keyboard choice. */
    val secondaryAction: NewTabDialogAction? = null,
)

/** The rows shown for this state, in the same order as the sections in the dialog. */
internal fun NewTabDialogState.choices(): List<NewTabChoice> = buildList {
    defaultShell?.let { shell ->
        add(
            NewTabChoice(
                key = "shell:${shell.command}",
                section = "Default",
                title = shell.name,
                subtitle = shell.command,
                trailing = newTabShortcutLabel,
                action = NewTabDialogAction.CreateNewTab(shell),
            ),
        )
    }
    connections.forEachIndexed { index, connection ->
        add(
            NewTabChoice(
                key = "connection:${connection.id}",
                section = "Connections".takeIf { index == 0 },
                title = connection.name,
                subtitle = connection.address,
                action = NewTabDialogAction.Connect(connection),
                secondaryAction = NewTabDialogAction.BrowseFiles(connection),
            ),
        )
    }
    add(
        NewTabChoice(
            key = "this-device",
            section = "Files",
            title = "This device",
            subtitle = "Browse this computer's files",
            action = NewTabDialogAction.BrowseThisDevice,
        ),
    )
    otherShells.forEachIndexed { index, shell ->
        add(
            NewTabChoice(
                key = "other-shell:${shell.command ?: shell.name}",
                section = "Other shells".takeIf { index == 0 },
                title = shell.name,
                subtitle = shell.command,
                action = NewTabDialogAction.CreateNewTab(shell),
            ),
        )
    }
}

/** `user@host`, plus the port when it isn't 22. */
private val SshConnection.address: String
    get() = if (port == DEFAULT_SSH_PORT) "$username@$host" else "$username@$host:$port"

/** Wraps around, like the quick-connect palette, so either arrow key can reach every row. */
internal fun moveSelection(current: Int, delta: Int, size: Int): Int {
    if (size == 0) return 0
    val moved = (current + delta) % size
    return if (moved < 0) moved + size else moved
}
