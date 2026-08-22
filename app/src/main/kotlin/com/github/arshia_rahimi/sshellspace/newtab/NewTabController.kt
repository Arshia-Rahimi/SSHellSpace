package com.github.arshia_rahimi.sshellspace.newtab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object NewTabController {
    private val _newTabButtonEvent = MutableSharedFlow<Unit>()
    val newTabButtonEvent = _newTabButtonEvent.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun emitNewTabButtonEvent() {
        scope.launch {
            _newTabButtonEvent.emit(Unit)
        }
    }
}
