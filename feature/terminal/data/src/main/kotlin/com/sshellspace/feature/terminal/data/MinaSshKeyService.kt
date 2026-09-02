package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.KeyValidationResult
import com.sshellspace.feature.terminal.domain.SshKeyService
import com.sshellspace.feature.terminal.domain.models.Algorithm
import com.sshellspace.feature.terminal.domain.models.KeyPair
import com.sshellspace.feature.terminal.domain.models.PrivateKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Security
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.util.security.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider

class MinaSshKeyService : SshKeyService {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun validate(key: PrivateKey, passphrase: String?): KeyValidationResult {
        var detectedEncryption = false
        var passphraseAttempted = false

        val passwordProvider = object : FilePasswordProvider {
            override fun getPassword(
                session: SessionContext?,
                resourceKey: org.apache.sshd.common.NamedResource?,
                retryIndex: Int
            ): String? {
                detectedEncryption = true
                // If retryIndex > 0, MINA SSHD already tried the passphrase once and it failed
                if (retryIndex > 0) {
                    return null
                }
                passphraseAttempted = true
                return passphrase
            }
        }

        return try {
            val parser = SecurityUtils.getKeyPairResourceParser()
            val inputStream = ByteArrayInputStream(key.toByteArray(Charsets.UTF_8))

            val keyPair = parser.loadKeyPairs(
                null,
                null,
                passwordProvider,
                inputStream
            ).firstOrNull()

            if (keyPair != null) {
                KeyValidationResult.Success(
                    isEncrypted = detectedEncryption
                )
            } else if (detectedEncryption && passphraseAttempted) {
                KeyValidationResult.InvalidPassphrase
            } else {
                KeyValidationResult.InvalidKeyFormat("No valid key pairs could be parsed from the input.")
            }
        } catch (e: Exception) {
            if (detectedEncryption && passphraseAttempted) {
                KeyValidationResult.InvalidPassphrase
            } else {
                KeyValidationResult.InvalidKeyFormat(
                    errorMessage = e.message ?: "Failed to decode private key format."
                )
            }
        }
    }

    override fun generate(
        comment: String,
        passphrase: String?,
        algorithm: Algorithm,
        keySize: Int,
    ): KeyPair {
        val keyPair = KeyUtils.generateKeyPair(algorithm.type, keySize)
        val publicKeyWithComment = "${PublicKeyEntry.toString(keyPair.public)} $comment"

        val outputStream = ByteArrayOutputStream()
        val writer = OpenSSHKeyPairResourceWriter.INSTANCE
        val options: OpenSSHKeyEncryptionContext? = passphrase?.let {
            OpenSSHKeyEncryptionContext().apply {
                password = passphrase
                cipherName = "AES"
                cipherType = "256"
            }
        }
        writer.writePrivateKey(keyPair, comment, options, outputStream)
        val privateKey = outputStream.toString(Charsets.UTF_8.name())

        return privateKey to publicKeyWithComment
    }
}
