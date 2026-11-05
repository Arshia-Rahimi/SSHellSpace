package com.sshellspace.config.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin

/** Starts the scheduled checks for updates. */
fun Koin.registerAppUpdates() {
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { get<AppUpdaterImpl>().run() }
}
