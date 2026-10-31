package com.sshellspace.core.data

import com.sshellspace.core.domain.models.SshConnection
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface SshConnectionDatasource {
    fun index(): Flow<List<SshConnection>>
    suspend fun get(id: Uuid): SshConnection?

    /** Inserts [connection], or replaces the stored one with the same id. */
    suspend fun upsert(connection: SshConnection)
    suspend fun delete(id: Uuid)
}
