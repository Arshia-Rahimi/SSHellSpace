package com.sshellspace.feature.vault.gate

import com.sshellspace.core.domain.models.VAULT_FILE_EXTENSION
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver

/** Lets the user pick a vault file to import. Null when cancelled. */
internal suspend fun pickVaultFile(): String? =
    FileKit.openFilePicker(type = FileKitType.File(VAULT_FILE_EXTENSION))?.absolutePath()

/** Lets the user choose where to export the vault [name]. Null when cancelled. */
internal suspend fun pickVaultExportFile(name: String): String? =
    FileKit.openFileSaver(suggestedName = name, extension = VAULT_FILE_EXTENSION)?.absolutePath()
