package com.sshellspace.core.data.vault

import com.sshellspace.core.common.JsonSerializer
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

private val MAGIC = "SSHV".toByteArray(Charsets.US_ASCII)
private const val FORMAT_VERSION: Byte = 1
private const val KEY_BYTES = 32
private const val NONCE_BYTES = 12
private const val TAG_BITS = 128
private const val SALT_BYTES = 16
private const val MAX_HEADER_BYTES = 64 * 1024
private const val MAX_FILE_BYTES = 64L * 1024 * 1024
private const val MAX_SLOTS = 8

// Argon2id at OWASP's recommended strength. Costs read from a file are capped, because a crafted file could otherwise
// ask for enough memory to take the app down when the user tries to import it.
private const val ARGON2_MEMORY_KIB = 64 * 1024
private const val ARGON2_ITERATIONS = 3
private const val ARGON2_PARALLELISM = 1
private const val MAX_ARGON2_MEMORY_KIB = 512 * 1024
private const val MAX_ARGON2_ITERATIONS = 8
private const val MAX_ARGON2_PARALLELISM = 8

private val secureRandom = SecureRandom()

internal fun randomBytes(size: Int) = ByteArray(size).also(secureRandom::nextBytes)

internal fun newVaultKey() = randomBytes(KEY_BYTES)

internal val VaultHeader.hasPassword: Boolean get() = slots.none { it is KeySlot.Unprotected }

/**
 * The readable part of a vault file. A random vault key encrypts the contents and each [KeySlot] holds that key
 * encrypted for one way of authenticating, so a new method or a new password never re-encrypts the data.
 */
@Serializable
internal data class VaultHeader(
    val id: String,
    val name: String,
    val slots: List<KeySlot>,
)

@Serializable
internal sealed interface KeySlot {
    @Serializable
    @SerialName("password")
    data class Password(
        val salt: String,
        @SerialName("memory_kib") val memoryKib: Int,
        val iterations: Int,
        val parallelism: Int,
        val nonce: String,
        @SerialName("wrapped_key") val wrappedKey: String,
    ) : KeySlot

    /**
     * A vault with no password: the vault key sits in the header in the clear. The file format and writes stay the same,
     * so a password can be added later, but this protects nothing against anyone holding the file.
     */
    @Serializable
    @SerialName("none")
    data class Unprotected(val key: String) : KeySlot
}

/**
 * A parsed vault file: magic, format version, header length, JSON [header], nonce, then the AES-256-GCM encrypted SQLite
 * database. The GCM tag also covers everything before the nonce, so an edited header makes the file fail to open.
 */
internal class VaultFile private constructor(
    val header: VaultHeader,
    private val authenticatedData: ByteArray,
    private val nonce: ByteArray,
    private val ciphertext: ByteArray,
) {
    /** The decrypted database. Only call this with a key from [unwrapKey]. */
    fun decrypt(key: ByteArray): ByteArray = try {
        aesGcm(Cipher.DECRYPT_MODE, key, nonce, authenticatedData).doFinal(ciphertext)
    } catch (_: GeneralSecurityException) {
        throw VaultException.Invalid("The vault file is damaged or was modified.")
    }

    companion object {
        /** Reads and parses [path], refusing a file too big to be a vault before loading it into memory. */
        fun read(path: Path): VaultFile {
            if (Files.size(path) > MAX_FILE_BYTES) throw VaultException.Invalid("This file is too large to be a vault.")
            return parse(Files.readAllBytes(path))
        }

        fun parse(bytes: ByteArray): VaultFile = try {
            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            if (!magic.contentEquals(MAGIC)) throw VaultException.Invalid("This isn't a vault file.")
            val version = buffer.get()
            if (version != FORMAT_VERSION) {
                throw VaultException.Invalid("This vault was made by a newer version of the app.")
            }
            val headerSize = buffer.getInt()
            if (headerSize !in 1..MAX_HEADER_BYTES) throw VaultException.Invalid("The vault file is damaged.")
            val headerBytes = ByteArray(headerSize).also(buffer::get)
            val authenticatedData = bytes.copyOf(buffer.position())
            val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val header = JsonSerializer.decodeFromString(VaultHeader.serializer(), headerBytes.decodeToString())
            if (header.slots.size !in 1..MAX_SLOTS) throw VaultException.Invalid("The vault file is damaged.")
            VaultFile(header, authenticatedData, nonce, ciphertext)
        } catch (_: BufferUnderflowException) {
            throw VaultException.Invalid("The vault file is damaged.")
        } catch (_: SerializationException) {
            throw VaultException.Invalid("The vault file is damaged.")
        }

        fun seal(header: VaultHeader, key: ByteArray, plaintext: ByteArray): ByteArray {
            val headerBytes = JsonSerializer.encodeToString(VaultHeader.serializer(), header).encodeToByteArray()
            val authenticatedData = ByteBuffer.allocate(MAGIC.size + 1 + Int.SIZE_BYTES + headerBytes.size)
                .put(MAGIC)
                .put(FORMAT_VERSION)
                .putInt(headerBytes.size)
                .put(headerBytes)
                .array()
            val nonce = randomBytes(NONCE_BYTES)
            val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, key, nonce, authenticatedData).doFinal(plaintext)
            return authenticatedData + nonce + ciphertext
        }
    }
}

/** The vault key from the slot [credential] opens. */
internal fun unwrapKey(header: VaultHeader, credential: VaultCredential): ByteArray = when (credential) {
    is VaultCredential.Password -> {
        val slots = header.slots.filterIsInstance<KeySlot.Password>()
        slots.firstNotNullOfOrNull { unwrapPasswordSlot(it, header.id, credential.value) }
            ?: throw VaultException.WrongCredential()
    }
    VaultCredential.None -> {
        val slot = header.slots.filterIsInstance<KeySlot.Unprotected>().firstOrNull()
            ?: throw VaultException.WrongCredential()
        try {
            slot.key.fromBase64().takeIf { it.size == KEY_BYTES }
        } catch (_: IllegalArgumentException) {
            null
        } ?: throw VaultException.Invalid("The vault file is damaged.")
    }
}

internal fun newKeySlot(vaultId: String, credential: VaultCredential, key: ByteArray): KeySlot = when (credential) {
    is VaultCredential.Password -> {
        val salt = randomBytes(SALT_BYTES)
        val nonce = randomBytes(NONCE_BYTES)
        val wrappingKey = argon2(credential.value, salt, ARGON2_MEMORY_KIB, ARGON2_ITERATIONS, ARGON2_PARALLELISM)
        val wrapped = aesGcm(Cipher.ENCRYPT_MODE, wrappingKey, nonce, vaultId.encodeToByteArray()).doFinal(key)
        wrappingKey.fill(0)
        KeySlot.Password(
            salt = salt.toBase64(),
            memoryKib = ARGON2_MEMORY_KIB,
            iterations = ARGON2_ITERATIONS,
            parallelism = ARGON2_PARALLELISM,
            nonce = nonce.toBase64(),
            wrappedKey = wrapped.toBase64(),
        )
    }
    VaultCredential.None -> KeySlot.Unprotected(key.toBase64())
}

/** Null when [password] isn't the one this slot was made with. */
private fun unwrapPasswordSlot(slot: KeySlot.Password, vaultId: String, password: String): ByteArray? {
    val costsAreSane = slot.memoryKib in 1..MAX_ARGON2_MEMORY_KIB &&
        slot.iterations in 1..MAX_ARGON2_ITERATIONS &&
        slot.parallelism in 1..MAX_ARGON2_PARALLELISM
    if (!costsAreSane) throw VaultException.Invalid("The vault file is damaged.")
    return try {
        val wrappingKey = argon2(password, slot.salt.fromBase64(), slot.memoryKib, slot.iterations, slot.parallelism)
        try {
            val cipher = aesGcm(Cipher.DECRYPT_MODE, wrappingKey, slot.nonce.fromBase64(), vaultId.encodeToByteArray())
            cipher.doFinal(slot.wrappedKey.fromBase64())
        } finally {
            wrappingKey.fill(0)
        }
    } catch (_: AEADBadTagException) {
        null
    } catch (_: GeneralSecurityException) {
        throw VaultException.Invalid("The vault file is damaged.")
    } catch (_: IllegalArgumentException) {
        throw VaultException.Invalid("The vault file is damaged.")
    } catch (_: OutOfMemoryError) {
        throw VaultException.Invalid("There isn't enough memory to open this vault.")
    }
}

private fun argon2(password: String, salt: ByteArray, memoryKib: Int, iterations: Int, parallelism: Int): ByteArray {
    val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withMemoryAsKB(memoryKib)
        .withIterations(iterations)
        .withParallelism(parallelism)
        .build()
    // Normalized so the same password typed on another OS or keyboard layout produces the same bytes.
    val passwordBytes = Normalizer.normalize(password, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
    val output = ByteArray(KEY_BYTES)
    Argon2BytesGenerator().apply { init(parameters) }.generateBytes(passwordBytes, output)
    passwordBytes.fill(0)
    return output
}

private fun aesGcm(mode: Int, key: ByteArray, nonce: ByteArray, authenticatedData: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        updateAAD(authenticatedData)
    }

private fun ByteArray.toBase64() = Base64.getEncoder().encodeToString(this)

private fun String.fromBase64() = Base64.getDecoder().decode(this)
