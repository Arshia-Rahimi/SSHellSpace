package com.sshellspace.config.update

import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Signs with the JDK, as the `signUpdates` task does, so this also shows the two implementations agree. */
class UpdateVerifierTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val verifier = UpdateVerifier(publicKeyOf(keys))
    private val artifact = Files.createTempFile("update", ".zip").toFile().apply { writeText("the release") }

    private fun publicKeyOf(pair: KeyPair) =
        Base64.getEncoder().encodeToString(pair.public.encoded.takeLast(32).toByteArray())

    private fun sign(version: String, file: File = artifact, with: KeyPair = keys): ByteArray {
        val signature = Signature.getInstance("Ed25519").run {
            initSign(with.private)
            update(updateSignatureMessage(version, file))
            sign()
        }
        return Base64.getEncoder().encode(signature)
    }

    @Test
    fun `the message has the format the release task signs`() {
        val hash = MessageDigest.getInstance("SHA-512").digest("the release".toByteArray()).toHexString()

        assertEquals("SSHellSpace update 1\n1.2.3\n$hash", updateSignatureMessage("1.2.3", artifact).decodeToString())
    }

    @Test
    fun `accepts a release signed with the key`() {
        assertTrue(verifier.isValid(artifact, "1.2.3", sign("1.2.3")))
    }

    @Test
    fun `tolerates the line ending a signature file might have`() {
        assertTrue(verifier.isValid(artifact, "1.2.3", sign("1.2.3") + '\n'.code.toByte()))
    }

    @Test
    fun `rejects a changed file`() {
        val signature = sign("1.2.3")
        artifact.appendText("!")

        assertFalse(verifier.isValid(artifact, "1.2.3", signature))
    }

    @Test
    fun `rejects an old release offered as a newer version`() {
        assertFalse(verifier.isValid(artifact, "9.9.9", sign("1.2.3")))
    }

    @Test
    fun `rejects another key`() {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

        assertFalse(verifier.isValid(artifact, "1.2.3", sign("1.2.3", with = other)))
    }

    @Test
    fun `rejects junk`() {
        assertFalse(verifier.isValid(artifact, "1.2.3", "not base64 !!".toByteArray()))
        assertFalse(verifier.isValid(artifact, "1.2.3", Base64.getEncoder().encode(ByteArray(3))))
        assertFalse(verifier.isValid(artifact, "1.2.3", ByteArray(0)))
    }
}
