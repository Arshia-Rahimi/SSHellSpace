package com.sshellspace.feature.vault.domain

import com.sshellspace.core.domain.models.PortForward
import com.sshellspace.core.domain.models.PortForwardType
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface PortForwardRepository {
    fun getForwards(): Flow<List<PortForward>>

    /** Creates a forward when [id] is null, otherwise replaces the forward with that id. */
    suspend fun save(id: Uuid?, draft: PortForwardDraft): SavePortForwardResult
    suspend fun delete(id: Uuid)
}

data class PortForwardDraft(
    val name: String,
    val connectionId: Uuid,
    val type: PortForwardType,
    val bindHost: String,
    val bindPort: Int,
    val destinationHost: String,
    val destinationPort: Int,
)

sealed interface SavePortForwardResult {
    data class Success(val forward: PortForward) : SavePortForwardResult
    data class Error(val message: String) : SavePortForwardResult
}
