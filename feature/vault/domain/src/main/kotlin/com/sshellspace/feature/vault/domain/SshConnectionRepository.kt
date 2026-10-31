package com.sshellspace.feature.vault.domain

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface SshConnectionRepository {
    fun getConnections(): Flow<List<SshConnection>>
    suspend fun get(id: Uuid): SshConnection?

    /** Creates a connection when [id] is null, otherwise replaces the connection with that id. */
    suspend fun save(id: Uuid?, draft: SshConnectionDraft): SaveSshConnectionResult

    /** Deletes the connection and clears it as the jump host of any connection that used it. */
    suspend fun delete(id: Uuid)
}

data class SshConnectionDraft(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshConnectionAuth,
    val options: SshConnectionOptions,
)

sealed interface SaveSshConnectionResult {
    data class Success(val connection: SshConnection) : SaveSshConnectionResult
    data class Error(val message: String) : SaveSshConnectionResult
}
