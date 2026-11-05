package com.sshellspace.config.update

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** The public half of the key `generateUpdateSigningKey` made. Replacing it strands every install that trusts the old one. */
private const val UPDATE_PUBLIC_KEY = "ltMRKy86RAFwM9ZdArELx9op7LDy5lyqlUFkCBnUszs="

/**
 * What a release signs. It names the version as well as the file: otherwise a tampered manifest could offer an old, genuine
 * release as a new one, bringing back whatever the later releases fixed.
 *
 * Keep in step with `signatureMessage` in build-logic.
 */
internal fun updateSignatureMessage(version: String, file: File): ByteArray {
    val digest = MessageDigest.getInstance("SHA-512")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    val hash = digest.digest().toHexString()
    return "SSHellSpace update 1\n$version\n$hash".toByteArray()
}

/**
 * Checks that a release came from whoever holds the signing key. The updater's own SHA-512 only proves a file matches a manifest
 * from the same host, so anyone able to change a release could change both, and this app can reach unlocked vaults.
 */
internal class UpdateVerifier(publicKey: String = UPDATE_PUBLIC_KEY) {
    private val key = Ed25519PublicKeyParameters(Base64.getDecoder().decode(publicKey))

    /** [signature] is the content of a `.sig` file, which comes from the same untrusted host as the file. */
    fun isValid(file: File, version: String, signature: ByteArray): Boolean {
        val decoded = try {
            Base64.getDecoder().decode(signature.decodeToString().trim())
        } catch (_: IllegalArgumentException) {
            return false
        }
        val message = updateSignatureMessage(version, file)
        return Ed25519Signer().run {
            init(false, key)
            update(message, 0, message.size)
            verifySignature(decoded)
        }
    }
}
