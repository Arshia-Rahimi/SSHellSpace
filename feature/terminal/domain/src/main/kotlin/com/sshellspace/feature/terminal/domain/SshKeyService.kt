package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.Algorithm
import com.sshellspace.feature.terminal.domain.models.DeviceSshKey
import com.sshellspace.feature.terminal.domain.models.SshKey
import com.sshellspace.feature.terminal.domain.models.VaultSshKey

interface SshKeyService {
    suspend fun validate(key: SshKey): KeyValidationResult
    suspend fun generateDeviceSshKey(
        comment: String,
        algorithm: Algorithm,
        passphrase: String? = null,
    ): DeviceSshKey
    suspend fun generateVaultSshKey(
        comment: String,
        algorithm: Algorithm,
    ): VaultSshKey
}

sealed interface KeyValidationResult {
    data class Success(
        val isEncrypted: Boolean,
    ) : KeyValidationResult

    data class InvalidPassphrase(
        val isEncrypted: Boolean = true
    ) : KeyValidationResult

    data class InvalidKeyFormat(
        val errorMessage: String
    ) : KeyValidationResult
}
