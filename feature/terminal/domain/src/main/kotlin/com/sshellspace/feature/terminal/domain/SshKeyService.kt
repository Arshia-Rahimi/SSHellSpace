package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.Algorithm
import com.sshellspace.feature.terminal.domain.models.SshKey

interface SshKeyService {
    suspend fun validate(key: SshKey): KeyValidationResult
    suspend fun generate(
        comment: String,
        algorithm: Algorithm,
        passphrase: String? = null,
    ): SshKey
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
