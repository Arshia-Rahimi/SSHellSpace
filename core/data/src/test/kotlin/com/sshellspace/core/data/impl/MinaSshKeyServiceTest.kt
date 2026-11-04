package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import java.io.ByteArrayOutputStream
import java.security.interfaces.RSAPublicKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.util.security.SecurityUtils

class MinaSshKeyServiceTest {
    private val service = MinaSshKeyService()

    @Test
    fun `generated RSA keys are big enough`() {
        val key = service.generate("user@test.com", algorithm = Algorithm.RSA, passphrase = null)
        val parsed = SecurityUtils.getKeyPairResourceParser()
            .loadKeyPairs(null, null, null, key.first.byteInputStream())
            .single()

        assertEquals(Algorithm.RSA.defaultKeySize, (parsed.public as RSAPublicKey).modulus.bitLength())
    }

    @Test
    fun `generating a small key is refused`() {
        assertFails { service.generate("user@test.com", null, Algorithm.RSA, keySize = 1024) }
    }

    @Test
    fun `a small existing RSA key is rejected with an explanation`() {
        val weak = KeyUtils.generateKeyPair("ssh-rsa", 1024)
        val encoded = ByteArrayOutputStream().also {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(weak, "", null, it)
        }.toString(Charsets.UTF_8.name())

        val result = assertIs<SshKeyValidationResult.InvalidSshKeyFormat>(service.validate(encoded))

        assertContains(result.errorMessage, "1024 bits")
    }

    @Test
    fun `validate ED25519 key generated without passphrase`() {
        val key = service.generate("user@test.com", algorithm = Algorithm.Ed25519, passphrase = null)
        assertEquals(service.validate(key.first), SshKeyValidationResult.Success(isEncrypted = false, algorithm = Algorithm.Ed25519))
    }

    @Test
    fun `validate RSA key generated without passphrase`() {
        val key = service.generate("user@test.com", algorithm = Algorithm.RSA, passphrase = null)
        assertEquals(service.validate(key.first), SshKeyValidationResult.Success(isEncrypted = false, Algorithm.RSA))
    }

    @Test
    fun `validate ED25519 key generated with passphrase`() {
        repeat(10) {
            val passphrase = Random.nextInt().toString()
            val key = service.generate("user@test.com", passphrase, Algorithm.Ed25519)
            assertEquals(service.validate(key.first, passphrase), SshKeyValidationResult.Success(isEncrypted = true, algorithm = Algorithm.Ed25519))
        }
    }

    @Test
    fun `validate RSA key generated with passphrase`() {
        repeat(10) {
            val passphrase = Random.nextInt().toString()
            val key = service.generate("user@test.com", passphrase, Algorithm.RSA)
            assertEquals(service.validate(key.first, passphrase), SshKeyValidationResult.Success(isEncrypted = true, algorithm = Algorithm.RSA))
        }
    }

    @Test
    fun `load decrypts key and returns matching public key`() {
        Algorithm.entries.forEach { algorithm ->
            val key = service.generate("user@test.com", "secret", algorithm)

            val loaded = service.load(key.first, "secret")

            assertEquals(SshKeyValidationResult.Success(isEncrypted = false, algorithm = algorithm), service.validate(loaded.first))
            assertEquals(key.second.substringBeforeLast(" "), loaded.second)
        }
    }

    @Test
    fun `load throws on wrong passphrase`() {
        val key = service.generate("user@test.com", "secret", Algorithm.Ed25519)

        assertFails { service.load(key.first, "wrong") }
    }

    @Test
    fun `load throws on invalid key`() {
        assertFails { service.load("not a key", null) }
    }
}
