package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.SshKeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals

class MinaSshKeyServiceTest {
    private val service = MinaSshKeyService()

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
