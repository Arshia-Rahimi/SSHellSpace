package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.KeyValidationResult
import com.sshellspace.core.domain.models.Algorithm
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class MinaSshKeyServiceTest {
    private val service = MinaSshKeyService()

    @Test
    fun `validate ED25519 key generated without passphrase`() {
        val key = service.generate("user@test.com", algorithm = Algorithm.ED25519)
        assertEquals(service.validate(key.first), KeyValidationResult.Success(isEncrypted = false))
    }

    @Test
    fun `validate RSA key generated without passphrase`() {
        val key = service.generate("user@test.com", algorithm = Algorithm.RSA)
        assertEquals(service.validate(key.first), KeyValidationResult.Success(isEncrypted = false))
    }

    @Test
    fun `validate ED25519 key generated with passphrase`() {
        repeat(10) {
            val passphrase = Random.nextInt().toString()
            val key = service.generate("user@test.com", passphrase, Algorithm.ED25519)
            assertEquals(service.validate(key.first, passphrase), KeyValidationResult.Success(isEncrypted = true))
        }
    }

    @Test
    fun `validate RSA key generated with passphrase`() {
        repeat(10) {
            val passphrase = Random.nextInt().toString()
            val key = service.generate("user@test.com", passphrase, Algorithm.RSA)
            assertEquals(service.validate(key.first, passphrase), KeyValidationResult.Success(isEncrypted = true))
        }
    }
}
