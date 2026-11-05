package com.sshellspace.feature.vault.domain

import com.sshellspace.core.domain.models.KnownHost
import kotlinx.coroutines.flow.Flow

/** The two lists of trusted host keys: the vault's own, and the file this computer's `ssh` tool uses. */
interface KnownHostRepository {
    fun getVaultHosts(): Flow<List<KnownHost>>

    /** Read again on each call, since `ssh` can change the file at any time. */
    suspend fun getDeviceHosts(): DeviceKnownHosts
    suspend fun removeFromVault(host: KnownHost)
    suspend fun importToVault(path: String): KnownHostImportResult
    suspend fun exportVault(path: String): KnownHostExportResult
}

data class DeviceKnownHosts(
    val path: String,
    val hosts: List<KnownHost> = emptyList(),
    val error: String? = null,
)

sealed interface KnownHostImportResult {
    data class Success(
        val added: Int,
        val alreadyKnown: Int,
        /** For a host this list already trusts a different key of that type for. */
        val conflicting: Int,
        /** Wildcards and certificate authorities, which this list doesn't take. */
        val notAllowed: Int,
        val unreadable: Int,
    ) : KnownHostImportResult

    data class Error(val message: String) : KnownHostImportResult
}

sealed interface KnownHostExportResult {
    data class Success(val count: Int) : KnownHostExportResult
    data class Error(val message: String) : KnownHostExportResult
}
