package com.sshellspace.core.data

import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.PrivateKey
import com.sshellspace.core.domain.models.SshKeyInfo
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface SshKeyDatasource {
    fun index(): Flow<List<SshKeyInfo>>
    suspend fun get(id: Uuid): SshKeyInfo?
    suspend fun insertDeviceKey(
        name: String,
        algorithm: Algorithm,
        path: String,
        passphrase: String?,
    ): SshKeyInfo
    suspend fun insertVaultKey(
        name: String,
        algorithm: Algorithm,
        privateKey: PrivateKey,
    ): SshKeyInfo
    suspend fun updateName(id: Uuid, name: String): SshKeyInfo?
    suspend fun delete(id: Uuid)
}
