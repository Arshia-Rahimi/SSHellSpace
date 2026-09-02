package com.sshellspace.feature.terminal.data

import com.sshellspace.feature.terminal.domain.KeyValidationResult
import com.sshellspace.feature.terminal.domain.models.Algorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MinaSshKeyServiceTest {

    private val service = MinaSshKeyService()
    private val scope = CoroutineScope(Dispatchers.Main)

    @Test
    fun `generate private key pair`() {
        Algorithm.entries.forEach {
            println(service.generateSshKeyPair(it, "user@test.com"))
        }
    }

    @Test
    fun `validate ED25519 key generated without passphrase returns true`() {
        scope.launch {
            val key = service.generate("user@test.com", Algorithm.ED25519)
            assertEquals(service.validate(key), KeyValidationResult.Success(isEncrypted = false))
        }
    }

    @Test
    fun `validate RSA key generated without passphrase returns true`() {
        scope.launch {
            val key = service.generate("user@test.com", Algorithm.RSA)
            assertEquals(service.validate(key), KeyValidationResult.Success(isEncrypted = false))
        }
    }

//    @Test
//    fun `generate produces independently usable keys with public key derived from private`() {
//        val first = generateVaultKey("round-trip-1", null, Algorithm.ED25519)
//        val second = generateVaultKey("round-trip-2", null, Algorithm.ED25519)
//
//        assertTrue(service.validate(first))
//        assertTrue(service.validate(second))
//    }
//
//    @Test
//    fun `validate garbage private key string returns false`() {
//        val key = VaultSshKey(
//            algorithm = Algorithm.ED25519,
//            passphrase = null,
//            privateKeyValue = "this is not a private key"
//        )
//
//        assertFalse(service.validate(key))
//    }
//
//    @Test
//    fun `validate nonexistent device key file returns false`() {
//        val key = DeviceSshKey(
//            passphrase = null,
//            algorithm = Algorithm.RSA,
//            privateKeyFilePath = Path("/nonexistent/path/to/key.pem")
//        )
//
//        assertFalse(service.validate(key))
//    }
//
//    @Test
//    fun `validate device key file written to temp dir returns true`() {
//        val key = generateVaultKey("device-key-test", null, Algorithm.RSA)
//        val tempFile = Files.createTempFile("sshkey", ".pem")
//        tempFile.writeText(key.privateKeyValue)
//
//        val deviceKey = DeviceSshKey(
//            passphrase = null,
//            algorithm = Algorithm.RSA,
//            privateKeyFilePath = Path(tempFile.toString())
//        )
//
//        assertTrue(service.validate(deviceKey))
//    }
//
//    @Test
//    fun `generated private key starts with OpenSSH header`() {
//        val key = generateVaultKey("header-test", "secret", Algorithm.ED25519)
//
//        assertTrue(
//            key.privateKeyValue.startsWith(
//                "-----BEGIN OPENSSH PRIVATE KEY-----"
//            )
//        )
//    }
//
//    @Test
//    fun `generated key contains only a private key not a separate public key`() {
//        val key = generateVaultKey("envelope-test", null, Algorithm.ED25519)
//
//        assertTrue(key.privateKeyValue.contains("PRIVATE KEY"))
//        assertFalse(key.privateKeyValue.contains("-----BEGIN PUBLIC KEY-----"))
//    }
//
//    @Test
//    fun `generated RSA key uses RSA algorithm tag`() {
//        val key = generateVaultKey("rsa-tag-test", null, Algorithm.RSA)
//
//        assertTrue(key.privateKeyValue.contains("ssh-rsa"))
//    }
//
//    @Test
//    fun `generated ed25519 key uses ed25519 algorithm tag`() {
//        val key = generateVaultKey("ed-tag-test", null, Algorithm.ED25519)
//
//        assertTrue(key.privateKeyValue.contains("ssh-ed25519"))
//    }
//
//    @Test
//    fun `correct passphrase yields validation while wrong passphrase rejects`() {
//        val key = generateVaultKey("support-test", "correct-horse", Algorithm.RSA)
//
//        val withCorrect = VaultSshKey(
//            algorithm = key.algorithm,
//            passphrase = "correct-horse",
//            privateKeyValue = key.privateKeyValue
//        )
//        val withWrong = VaultSshKey(
//            algorithm = key.algorithm,
//            passphrase = "battery-staple",
//            privateKeyValue = key.privateKeyValue
//        )
//
//        assertTrue(service.validate(withCorrect))
//        assertFalse(service.validate(withWrong))
//    }
}
