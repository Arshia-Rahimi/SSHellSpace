package com.sshellspace.core.domain.models

sealed interface AppUpdateState {
    /** This install can't update itself, such as a development run. */
    data object Unsupported : AppUpdateState
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState

    /** A new version exists, and isn't downloaded because automatic updates are off. */
    data class Available(val version: String) : AppUpdateState
    data class Downloading(val version: String, val percent: Int) : AppUpdateState

    /** Downloaded and verified, waiting to be installed. */
    data class Ready(val version: String) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}
