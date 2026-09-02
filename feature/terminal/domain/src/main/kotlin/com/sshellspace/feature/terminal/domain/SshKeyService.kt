package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.Algorithm
import com.sshellspace.feature.terminal.domain.models.KeyPair
import com.sshellspace.feature.terminal.domain.models.PrivateKey
import com.sshellspace.feature.terminal.domain.models.PublicKey

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
