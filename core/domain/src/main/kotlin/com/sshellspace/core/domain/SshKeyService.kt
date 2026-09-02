package com.sshellspace.core.domain

import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.KeyPair
import com.sshellspace.core.domain.models.PrivateKey

interface SshKeyService {
    fun validate(key: PrivateKey, passphrase: String? = null): KeyValidationResult
    fun generate(
        comment: String,
        passphrase: String? = null,
        algorithm: Algorithm = Algorithm.ED25519,
        keySize: Int = 256,
    ): KeyPair
}

sealed interface KeyValidationResult {
    data class Success(
        val isEncrypted: Boolean,
    ) : KeyValidationResult

    data object InvalidPassphrase : KeyValidationResult

    data class InvalidKeyFormat(
        val errorMessage: String
    ) : KeyValidationResult
}
