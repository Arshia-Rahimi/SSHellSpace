package com.sshellspace.feature.vault.settings

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker

/** Lets the user pick a theme JSON file. Null when cancelled. */
internal suspend fun pickThemeFile(): String? =
    FileKit.openFilePicker(type = FileKitType.File("json"))?.absolutePath()
