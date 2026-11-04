package com.sshellspace.feature.terminal.ui.screen.sftp

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import java.nio.file.Path

/** Lets the user pick files to upload. Null when cancelled. */
internal suspend fun pickUploadFiles(): List<Path>? =
    FileKit.openFilePicker(mode = FileKitMode.Multiple())?.map { Path.of(it.absolutePath()) }

/** Lets the user pick a folder, to upload or to download into. Null when cancelled. */
internal suspend fun pickFolder(): Path? =
    FileKit.openDirectoryPicker()?.let { Path.of(it.absolutePath()) }
