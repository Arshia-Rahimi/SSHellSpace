package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.PortForwardDatasource
import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.generateUuid
import com.sshellspace.core.domain.models.LOCALHOST
import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import com.sshellspace.feature.vault.domain.PortForwardDraft
import com.sshellspace.feature.vault.domain.PortForwardRepository
import com.sshellspace.feature.vault.domain.SavePortForwardResult
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PortForwardRepositoryImpl(
    private val forwardDatasource: PortForwardDatasource,
    private val connectionDatasource: SshConnectionDatasource,
) : PortForwardRepository {
    override fun getForwards(): Flow<List<PortForward>> =
        forwardDatasource.index()

    override suspend fun save(id: Uuid?, draft: PortForwardDraft): SavePortForwardResult {
        val forwardId = id ?: generateUuid()
        validate(forwardId, draft)?.let { return SavePortForwardResult.Error(it) }

        val dynamic = draft.type == PortForwardType.Dynamic
        val forward = PortForward(
            id = forwardId,
            name = draft.name.trim(),
            connectionId = draft.connectionId,
            type = draft.type,
            bindHost = draft.bindHost.trim().ifEmpty { LOCALHOST },
            bindPort = draft.bindPort,
            destinationHost = if (dynamic) LOCALHOST else draft.destinationHost.trim(),
            destinationPort = if (dynamic) 0 else draft.destinationPort,
        )
        return try {
            forwardDatasource.upsert(forward)
            SavePortForwardResult.Success(forward)
        } catch (e: Exception) {
            SavePortForwardResult.Error(e.message ?: "Failed to save port forward")
        }
    }

    override suspend fun delete(id: Uuid) {
        forwardDatasource.delete(id)
    }

    private suspend fun validate(id: Uuid, draft: PortForwardDraft): String? {
        val forwards = forwardDatasource.index().first()
        val needsDestination = draft.type != PortForwardType.Dynamic
        return when {
            draft.name.isBlank() -> "Name cannot be empty"
            forwards.any { it.id != id && it.name.equals(draft.name.trim(), ignoreCase = true) } ->
                "${draft.name.trim()} already exists"
            connectionDatasource.get(draft.connectionId) == null -> "The selected connection no longer exists"
            draft.bindPort !in PORT_RANGE -> "Port must be between 1 and 65535"
            needsDestination && draft.destinationHost.isBlank() -> "Destination host cannot be empty"
            needsDestination && draft.destinationPort !in PORT_RANGE -> "Destination port must be between 1 and 65535"
            else -> null
        }
    }

    private companion object {
        val PORT_RANGE = 1..65535
    }
}
