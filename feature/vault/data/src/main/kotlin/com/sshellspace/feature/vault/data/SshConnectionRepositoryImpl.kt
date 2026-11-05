package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.PortForwardDatasource
import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.data.generateUuid
import com.sshellspace.core.data.isPlainHost
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.feature.vault.domain.SaveSshConnectionResult
import com.sshellspace.feature.vault.domain.SshConnectionDraft
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SshConnectionRepositoryImpl(
    private val connectionDatasource: SshConnectionDatasource,
    private val sshKeyDatasource: SshKeyDatasource,
    private val portForwardDatasource: PortForwardDatasource,
) : SshConnectionRepository {
    override fun getConnections(): Flow<List<SshConnection>> =
        connectionDatasource.index()

    override suspend fun get(id: Uuid): SshConnection? =
        connectionDatasource.get(id)

    override suspend fun save(id: Uuid?, draft: SshConnectionDraft): SaveSshConnectionResult {
        val connectionId = id ?: generateUuid()
        validate(connectionId, draft)?.let { return SaveSshConnectionResult.Error(it) }

        val connection = SshConnection(
            id = connectionId,
            name = draft.name.trim(),
            host = draft.host.trim(),
            port = draft.port,
            username = draft.username.trim(),
            auth = draft.auth,
            options = draft.options,
        )
        return try {
            connectionDatasource.upsert(connection)
            SaveSshConnectionResult.Success(connection)
        } catch (e: Exception) {
            SaveSshConnectionResult.Error(e.message ?: "Failed to save connection")
        }
    }

    override suspend fun delete(id: Uuid) {
        connectionDatasource.index().first()
            .filter { it.options.jumpHostId == id }
            .forEach { connectionDatasource.upsert(it.copy(options = it.options.copy(jumpHostId = null))) }
        portForwardDatasource.deleteForConnection(id)
        connectionDatasource.delete(id)
    }

    private suspend fun validate(id: Uuid, draft: SshConnectionDraft): String? {
        val connections = connectionDatasource.index().first().associateBy { it.id }
        val auth = draft.auth
        return when {
            draft.name.isBlank() -> "Name cannot be empty"
            draft.host.isBlank() -> "Host cannot be empty"
            !isPlainHost(draft.host.trim()) -> "Host must be a host name or IP address"
            draft.username.isBlank() -> "Username cannot be empty"
            draft.port !in PORT_RANGE -> "Port must be between 1 and 65535"
            connections.values.any { it.id != id && it.name.equals(draft.name.trim(), ignoreCase = true) } ->
                "${draft.name.trim()} already exists"
            auth is SshConnectionAuth.StoredKey && sshKeyDatasource.get(auth.keyId) == null ->
                "The selected SSH key no longer exists"
            auth is SshConnectionAuth.KeyFile && auth.path.isBlank() -> "Key file path cannot be empty"
            else -> jumpHostError(id, draft.options.jumpHostId, connections)
        }
    }

    /** Rejects a missing jump host and chains that would loop back to [id]. */
    private fun jumpHostError(id: Uuid, jumpHostId: Uuid?, connections: Map<Uuid, SshConnection>): String? {
        var next = jumpHostId ?: return null
        if (next !in connections) return "The selected jump host no longer exists"
        val visited = mutableSetOf(id)
        while (true) {
            if (!visited.add(next)) return "Jump hosts can't form a loop"
            next = connections[next]?.options?.jumpHostId ?: return null
        }
    }

    private companion object {
        val PORT_RANGE = 1..65535
    }
}
