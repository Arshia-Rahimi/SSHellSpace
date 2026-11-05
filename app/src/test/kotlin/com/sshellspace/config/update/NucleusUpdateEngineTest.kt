package com.sshellspace.config.update

import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sun.net.httpserver.HttpServer
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GenericProvider
import io.mockk.every
import io.mockk.mockk
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** Runs the real Nucleus updater against a local server laid out like a release, using electron-builder's manifest format. */
class NucleusUpdateEngineTest {
    private val artifact = ByteArray(200_000) { (it % 251).toByte() }
    private val artifactName = "SSHellSpace-1.1.0-universal.zip"
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val files = mutableMapOf<String, ByteArray>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = files[exchange.requestURI.path.removePrefix("/")]
            exchange.sendResponseHeaders(if (body == null) 404 else 200, body?.size?.toLong() ?: -1)
            body?.let { bytes -> exchange.responseBody.use { it.write(bytes) } }
            exchange.close()
        }
        start()
    }
    private val cache = Files.createTempDirectory("update-cache").toFile()
    private val engine = NucleusUpdateEngine {
        provider = GenericProvider("http://127.0.0.1:${server.address.port}")
        currentVersion = "1.0.0"
        executableType = "zip"
        cacheDir = cache
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        cache.deleteRecursively()
    }

    private fun publish(version: String = "1.1.0", content: ByteArray = artifact) {
        val sha512 = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(content))
        val manifest = """
            version: $version
            files:
              - url: $artifactName
                sha512: $sha512
                size: ${content.size}
            path: $artifactName
            sha512: $sha512
            releaseDate: '2026-11-05T00:00:00.000Z'
        """.trimIndent().toByteArray()
        listOf("latest.yml", "latest-mac.yml", "latest-linux.yml").forEach { files[it] = manifest }
        files[artifactName] = content
    }

    private fun signature(version: String = "1.1.0"): ByteArray {
        val file = Files.createTempFile("artifact", ".zip").toFile().apply { writeBytes(artifact) }
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keys.private)
            update(updateSignatureMessage(version, file))
            sign()
        }
        file.delete()
        return Base64.getEncoder().encode(signature)
    }

    private fun updater(): AppUpdaterImpl {
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded.takeLast(32).toByteArray())
        val preferences = mockk<CorePreferencesRepository>(relaxed = true) {
            every { getAutoUpdate() } returns flowOf(true)
            every { getLastUpdateCheck() } returns flowOf(null)
        }
        return AppUpdaterImpl(engine, UpdateVerifier(publicKey), preferences, mockk(relaxed = true), mockk(relaxed = true), { mockk() })
    }

    @Test
    fun `finds a newer release, and picks the artifact from the manifest`() = runTest {
        publish()

        val result = assertIs<UpdateResult.Available>(withContext(Dispatchers.IO) { engine.check() })

        assertEquals("1.1.0", result.info.version)
        assertEquals(artifactName, result.info.currentFile.fileName)
    }

    @Test
    fun `ignores a release that isn't newer`() = runTest {
        publish(version = "1.0.0")

        assertEquals(UpdateResult.NotAvailable, withContext(Dispatchers.IO) { engine.check() })
    }

    @Test
    fun `reads the signature beside the artifact, and null if there isn't one`() = runTest {
        publish()
        val info = assertIs<UpdateResult.Available>(withContext(Dispatchers.IO) { engine.check() }).info

        assertNull(engine.signatureFor(info))

        files["$artifactName.sig"] = "signature".toByteArray()
        assertEquals("signature", engine.signatureFor(info)?.decodeToString())
    }

    @Test
    fun `reads no more of a signature than one could need`() = runTest {
        publish()
        val info = assertIs<UpdateResult.Available>(withContext(Dispatchers.IO) { engine.check() }).info
        files["$artifactName.sig"] = ByteArray(1_000_000)

        assertEquals(1024, engine.signatureFor(info)?.size)
    }

    @Test
    fun `downloads the artifact to a file with the release's content`() = runTest {
        publish()
        val info = assertIs<UpdateResult.Available>(withContext(Dispatchers.IO) { engine.check() }).info

        val file = checkNotNull(withContext(Dispatchers.IO) { engine.download(info).last() }.file)

        assertEquals(artifact.toList(), file.readBytes().toList())
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `a genuine release ends up ready to install`() = runTest {
        publish()
        files["$artifactName.sig"] = signature()
        val updater = updater()

        withContext(Dispatchers.IO) { updater.checkNow(download = true) }

        assertEquals(AppUpdateState.Ready("1.1.0"), updater.state.value)
    }

    @Test
    fun `a release signed by someone else doesn't`() = runTest {
        publish()
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        files["$artifactName.sig"] = Base64.getEncoder().encode(
            Signature.getInstance("Ed25519").run {
                initSign(other.private)
                update("anything".toByteArray())
                sign()
            },
        )
        val updater = updater()

        withContext(Dispatchers.IO) { updater.checkNow(download = true) }

        assertIs<AppUpdateState.Failed>(updater.state.value)
    }

    @Test
    fun `a file whose checksum doesn't match the manifest fails the download`() = runTest {
        publish()
        files["$artifactName.sig"] = signature()
        files[artifactName] = ByteArray(artifact.size)
        val updater = updater()

        withContext(Dispatchers.IO) { updater.checkNow(download = true) }

        assertIs<AppUpdateState.Failed>(updater.state.value)
    }
}
