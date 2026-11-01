package com.sshellspace.feature.vault.sshkeys

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import java.io.File

/**
 * Where the pickers open: the SSH directory when it exists, else the home folder. OpenSSH uses `~/.ssh` on every
 * platform, Windows included (`%USERPROFILE%\.ssh`, which is the JVM's `user.home`).
 */
private fun startDirectory(): PlatformFile {
    val dir = File(DEFAULT_SSH_DIR).takeIf(File::isDirectory) ?: File(System.getProperty("user.home"))
    return PlatformFile(dir)
}

/** Lets the user pick a private key file, starting in the SSH directory. Null when cancelled. */
internal suspend fun pickSshKeyFile(): String? =
    FileKit.openFilePicker(directory = startDirectory())?.absolutePath()

/** Lets the user pick a folder for a new key, starting in the SSH directory. Null when cancelled. */
internal suspend fun pickSshKeyDirectory(): String? =
    FileKit.openDirectoryPicker(directory = startDirectory())?.absolutePath()
