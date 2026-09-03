package com.sshellspace.core.domain

import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.KeyPair
import com.sshellspace.core.domain.models.PrivateKey

interface SshKeyService {
    fun validate(key: PrivateKey, passphrase: String? = null): SshKeyValidationResult
    fun generate(
        comment: String,
        passphrase: String?,
        algorithm: Algorithm,
        keySize: Int = 256,
    ): KeyPair
}

sealed interface SshKeyValidationResult {
    data class Success(
        val isEncrypted: Boolean,
        val algorithm: Algorithm,
    ) : SshKeyValidationResult

    data object InvalidPassphrase : SshKeyValidationResult

    data class InvalidSshKeyFormat(
        val errorMessage: String
    ) : SshKeyValidationResult
}
