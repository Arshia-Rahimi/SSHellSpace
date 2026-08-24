package com.sshellspace.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

object EventBus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        scope.launch { _events.emit(event) }
    }
}

interface Event

fun Event.emit() = EventBus.emit(this)

inline fun <reified T : Event> T.listen(): Flow<T> = EventBus.events.filterIsInstance<T>()
