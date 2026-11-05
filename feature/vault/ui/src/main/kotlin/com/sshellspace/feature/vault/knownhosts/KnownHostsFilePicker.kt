package com.sshellspace.feature.vault.knownhosts

import com.sshellspace.feature.vault.sshkeys.sshDirectoryOrHome
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver

/** Lets the user pick a file in the known_hosts format, starting where that file normally is. Null when cancelled. */
internal suspend fun pickKnownHostsFile(): String? =
    FileKit.openFilePicker(directory = sshDirectoryOrHome())?.absolutePath()

/** Lets the user choose where to export the vault's list. Null when cancelled. */
internal suspend fun pickKnownHostsExportFile(): String? =
    FileKit.openFileSaver(suggestedName = "known_hosts")?.absolutePath()
