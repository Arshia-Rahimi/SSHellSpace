package com.sshellspace.core.data

import com.sshellspace.core.domain.models.PortForward
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface PortForwardDatasource {
    fun index(): Flow<List<PortForward>>
    suspend fun get(id: Uuid): PortForward?

    /** Inserts [forward], or replaces the stored one with the same id. */
    suspend fun upsert(forward: PortForward)
    suspend fun delete(id: Uuid)
    suspend fun deleteForConnection(connectionId: Uuid)
}
