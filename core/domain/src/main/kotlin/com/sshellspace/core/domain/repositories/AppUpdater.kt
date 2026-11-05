package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.AppUpdateState
import kotlinx.coroutines.flow.StateFlow

interface AppUpdater {
    val currentVersion: String
    val state: StateFlow<AppUpdateState>

    /** Checks now, whatever the settings say. A new version is only downloaded if automatic updates are on. */
    fun check()

    /** Downloads the version in [AppUpdateState.Available]. */
    fun download()

    /** The tabs and port forwards that installing would close. */
    fun openSessionCount(): Int

    /** Locks the vault, then quits and installs the version in [AppUpdateState.Ready]. Returns if it fails to start. */
    fun restartToInstall()

    /** Installs a downloaded version if automatic updates are on. Call as the app is quitting. */
    fun installOnQuit()
}
