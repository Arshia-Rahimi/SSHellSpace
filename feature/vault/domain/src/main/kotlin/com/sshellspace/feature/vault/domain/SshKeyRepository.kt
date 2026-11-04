package com.sshellspace.feature.vault.domain

import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.PrivateKey
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.PublicKey
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface SshKeyRepository {
    fun getKeys(): Flow<List<SshKeyInfo>>
    fun validate(privateKey: PrivateKey, passphrase: String?): SshKeyValidationResult
    suspend fun createVaultKey(name: String, comment: String, algorithm: Algorithm): CreateSshKeyResult
    suspend fun createDeviceKey(
        name: String,
        comment: String,
        fileName: String?,
        path: String,
        algorithm: Algorithm,
        passphrase: String? = null,
    ): CreateSshKeyResult
    suspend fun importDeviceKey(name: String, path: String, passphrase: String?): CreateSshKeyResult
    suspend fun importVaultKey(name: String, privateKey: PrivateKey, passphrase: String?): CreateSshKeyResult
    suspend fun getDetails(id: Uuid): SshKeyDetails?
    suspend fun updateName(id: Uuid, name: String)
    suspend fun delete(id: Uuid)
}

sealed interface CreateSshKeyResult {
    data object Success : CreateSshKeyResult
    data class Error(val message: String? = null) : CreateSshKeyResult
}

data class SshKeyDetails(
    val info: SshKeyInfo,
    val publicKey: PublicKey?,
    val path: String?,
    val error: String? = null,
)
