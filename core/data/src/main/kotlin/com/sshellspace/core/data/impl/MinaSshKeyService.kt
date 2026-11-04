package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.KeyPair
import com.sshellspace.core.domain.models.PrivateKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Security
import java.security.interfaces.RSAPublicKey
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.util.security.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal class MinaSshKeyService : SshKeyService {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun validate(key: PrivateKey, passphrase: String?): SshKeyValidationResult {
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

            val weakRsaBits = (keyPair?.public as? RSAPublicKey)?.modulus?.bitLength()
                ?.takeIf { it < Algorithm.RSA.minKeySize }
            if (weakRsaBits != null) {
                SshKeyValidationResult.InvalidSshKeyFormat(
                    "This RSA key is only $weakRsaBits bits, which can be broken. " +
                        "Generate a new key and replace this one on your servers.",
                )
            } else if (keyPair != null) {
                val rawAlgorithm = keyPair.private.algorithm
                val normalizedAlgorithm = when {
                    rawAlgorithm.equals("EdDSA", ignoreCase = true) ||
                            rawAlgorithm.equals("Ed25519", ignoreCase = true) -> Algorithm.Ed25519
                    else -> Algorithm.RSA
                }
                SshKeyValidationResult.Success(
                    isEncrypted = detectedEncryption,
                    algorithm = normalizedAlgorithm,
                )
            } else if (detectedEncryption && passphraseAttempted) {
                SshKeyValidationResult.InvalidPassphrase
            } else {
                SshKeyValidationResult.InvalidSshKeyFormat("No valid key pairs could be parsed from the input.")
            }
        } catch (e: Exception) {
            if (detectedEncryption && passphraseAttempted) {
                SshKeyValidationResult.InvalidPassphrase
            } else {
                SshKeyValidationResult.InvalidSshKeyFormat(
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
        require(keySize >= algorithm.minKeySize) { "$algorithm keys need at least ${algorithm.minKeySize} bits." }
        return encode(KeyUtils.generateKeyPair(algorithm.type, keySize), comment, passphrase)
    }

    override fun load(privateKey: PrivateKey, passphrase: String?): KeyPair {
        val keyPair = SecurityUtils.getKeyPairResourceParser().loadKeyPairs(
            null,
            null,
            FilePasswordProvider.of(passphrase),
            ByteArrayInputStream(privateKey.toByteArray(Charsets.UTF_8)),
        ).firstOrNull() ?: throw IllegalArgumentException("No valid key pairs could be parsed from the input.")
        return encode(keyPair, comment = "", passphrase = null)
    }

    private fun encode(
        keyPair: java.security.KeyPair,
        comment: String,
        passphrase: String?,
    ): KeyPair {
        val publicKeyWithComment = "${PublicKeyEntry.toString(keyPair.public)} $comment".trimEnd()

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
