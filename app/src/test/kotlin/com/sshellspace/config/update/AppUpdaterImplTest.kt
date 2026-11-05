package com.sshellspace.config.update

import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.PortForwardingService
import dev.nucleusframework.updater.DownloadProgress
import dev.nucleusframework.updater.UpdateFile
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.exception.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val HOUR = 3_600_000L

class AppUpdaterImplTest {
    private class FakeEngine(val result: () -> UpdateResult) : UpdateEngine {
        override val currentVersion = "1.0.0"
        override var isSupported = true
        var checks = 0
        var signature: ByteArray? = null
        var download: (UpdateInfo) -> Flow<DownloadProgress> = { flowOf() }
        val installs = mutableListOf<Pair<File, Boolean>>()
        var installFailure: Exception? = null

        override suspend fun check(): UpdateResult {
            checks++
            return result()
        }

        override fun download(info: UpdateInfo) = download.invoke(info)
        override suspend fun signatureFor(info: UpdateInfo) = signature
        override fun install(file: File, restart: Boolean) {
            installFailure?.let { throw it }
            installs += file to restart
        }
    }

    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val verifier = UpdateVerifier(Base64.getEncoder().encodeToString(keys.public.encoded.takeLast(32).toByteArray()))
    private val autoUpdate = MutableStateFlow(true)
    private val lastCheck = MutableStateFlow<Long?>(null)
    private val preferences = mockk<CorePreferencesRepository>(relaxed = true) {
        every { getAutoUpdate() } returns autoUpdate
        every { getUpdateCheckIntervalHours() } returns flowOf(24)
        every { getLastUpdateCheck() } returns lastCheck
        coEvery { setLastUpdateCheck(any()) } answers { lastCheck.value = firstArg() }
    }
    private val vaultManager = mockk<VaultManager>(relaxed = true)
    private val tabManager = mockk<TabManager>(relaxed = true) { every { openTabs() } returns emptyList() }
    private val forwards = mockk<PortForwardingService>(relaxed = true) { every { states } returns MutableStateFlow(emptyMap()) }
    private var clock = 100 * HOUR

    private fun updater(engine: FakeEngine) =
        AppUpdaterImpl(engine, verifier, preferences, vaultManager, tabManager, { forwards }, { clock })

    private fun release(version: String = "1.1.0", fileName: String = "SSHellSpace-mac.zip"): UpdateResult.Available {
        val file = UpdateFile(url = "https://example.test/$fileName", sha512 = "", size = 1, fileName = fileName)
        return UpdateResult.Available(UpdateInfo(version, "", listOf(file), file), UpdateLevel.MINOR)
    }

    private fun artifact(content: String = "the release"): File =
        Files.createTempDirectory("update").resolve("SSHellSpace-mac.zip").toFile().apply { writeText(content) }

    private fun signatureFor(file: File, version: String = "1.1.0"): ByteArray {
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keys.private)
            update(updateSignatureMessage(version, file))
            sign()
        }
        return Base64.getEncoder().encode(signature)
    }

    private fun downloadOf(file: File): (UpdateInfo) -> Flow<DownloadProgress> = {
        flowOf(DownloadProgress(1, 1, 100.0, file = file))
    }

    private fun FakeEngine.serving(file: File, signedAs: String = "1.1.0") = apply {
        signature = signatureFor(file, signedAs)
        download = downloadOf(file)
    }

    private suspend fun AppUpdaterImpl.awaitState(predicate: (AppUpdateState) -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { state.first(predicate) }
    }

    /** Runs [AppUpdaterImpl.run] for as long as [block], since that's what picks up the settings `installOnQuit` reads. */
    private suspend fun whileRunning(updater: AppUpdaterImpl, block: suspend () -> Unit) = withContext(Dispatchers.Default) {
        val job = launch { updater.run() }
        delay(100)
        block()
        job.cancelAndJoin()
    }

    private suspend fun FakeEngine.awaitInstall() = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (installs.isEmpty()) delay(5) }
    }

    @Test
    fun `no version available means up to date, and counts as a check`() = runTest {
        val updater = updater(FakeEngine { UpdateResult.NotAvailable })

        updater.checkNow(download = true)

        assertEquals(AppUpdateState.UpToDate, updater.state.value)
        assertEquals(clock, lastCheck.value)
    }

    @Test
    fun `an install that can't update itself says so`() = runTest {
        val engine = FakeEngine { UpdateResult.NotAvailable }.apply { isSupported = false }

        assertEquals(AppUpdateState.Unsupported, updater(engine).state.value)
    }

    @Test
    fun `a failed check shows an error and isn't recorded, so it's retried`() = runTest {
        val updater = updater(FakeEngine { UpdateResult.Error(NetworkException("offline")) })

        updater.checkNow(download = true)

        assertIs<AppUpdateState.Failed>(updater.state.value)
        assertEquals(null, lastCheck.value)
    }

    @Test
    fun `a genuine release is downloaded and ready`() = runTest {
        val updater = updater(FakeEngine { release() }.serving(artifact()))

        updater.checkNow(download = true)

        assertEquals(AppUpdateState.Ready("1.1.0"), updater.state.value)
        assertEquals(clock, lastCheck.value)
    }

    @Test
    fun `with automatic updates off a release is only announced until asked for`() = runTest {
        val engine = FakeEngine { release() }.serving(artifact())
        val updater = updater(engine)

        updater.checkNow(download = false)
        assertEquals(AppUpdateState.Available("1.1.0"), updater.state.value)

        updater.download()
        updater.awaitState { it == AppUpdateState.Ready("1.1.0") }
    }

    @Test
    fun `a tampered file is rejected and deleted, and can't be installed`() = runTest {
        val file = artifact()
        val engine = FakeEngine { release() }.serving(file)
        file.appendText("tampered")
        val updater = updater(engine)

        updater.checkNow(download = true)
        updater.restartToInstall()

        assertIs<AppUpdateState.Failed>(updater.state.value)
        assertFalse(file.exists())
        assertTrue(engine.installs.isEmpty())
    }

    @Test
    fun `an old genuine release offered as newer is rejected`() = runTest {
        val updater = updater(FakeEngine { release("9.9.9") }.serving(artifact(), signedAs = "1.1.0"))

        updater.checkNow(download = true)

        assertIs<AppUpdateState.Failed>(updater.state.value)
    }

    @Test
    fun `a release with no signature isn't downloaded`() = runTest {
        var downloads = 0
        val engine = FakeEngine { release() }.apply {
            signature = null
            download = {
                downloads++
                flowOf()
            }
        }
        val updater = updater(engine)

        updater.checkNow(download = true)

        assertIs<AppUpdateState.Failed>(updater.state.value)
        assertEquals(0, downloads)
    }

    @Test
    fun `a file name that could leave the staging folder is refused before downloading`() = runTest {
        listOf("../../.ssh/authorized_keys", "..", "a/b.zip", "a\\b.zip", ".hidden", "a b.zip").forEach { name ->
            var downloads = 0
            val engine = FakeEngine { release(fileName = name) }.apply {
                signature = ByteArray(1)
                download = {
                    downloads++
                    flowOf()
                }
            }
            val updater = updater(engine)

            updater.checkNow(download = true)

            assertIs<AppUpdateState.Failed>(updater.state.value, name)
            assertEquals(0, downloads, name)
        }
    }

    @Test
    fun `a version that isn't one is refused`() = runTest {
        val updater = updater(FakeEngine { release(version = "1.1.0\nVault password:") }.serving(artifact()))

        updater.checkNow(download = true)

        assertIs<AppUpdateState.Failed>(updater.state.value)
    }

    @Test
    fun `a download bigger than any real release is stopped`() = runTest {
        val engine = FakeEngine { release() }.apply {
            signature = ByteArray(1)
            download = { flow { emit(DownloadProgress(Long.MAX_VALUE, 1, 0.0)) } }
        }
        val updater = updater(engine)

        updater.checkNow(download = true)

        assertIs<AppUpdateState.Failed>(updater.state.value)
    }

    @Test
    fun `a failed download keeps an update that's already waiting`() = runTest {
        var result: UpdateResult = release("1.1.0")
        val engine = FakeEngine { result }.serving(artifact())
        val updater = updater(engine)
        updater.checkNow(download = true)

        result = release("1.2.0")
        engine.download = { flow { throw IOException("offline") } }
        engine.signature = ByteArray(1)
        updater.checkNow(download = true)

        assertEquals(AppUpdateState.Ready("1.1.0"), updater.state.value)
    }

    @Test
    fun `a newer release replaces a waiting one and removes its file`() = runTest {
        var result: UpdateResult = release("1.1.0")
        val first = artifact("first")
        val engine = FakeEngine { result }.serving(first)
        val updater = updater(engine)
        updater.checkNow(download = true)

        result = release("1.2.0")
        engine.serving(artifact("second"), signedAs = "1.2.0")
        updater.checkNow(download = true)

        assertEquals(AppUpdateState.Ready("1.2.0"), updater.state.value)
        assertFalse(first.exists())
    }

    @Test
    fun `checking again doesn't download what's already waiting`() = runTest {
        var downloads = 0
        val file = artifact()
        val engine = FakeEngine { release() }.apply {
            signature = signatureFor(file)
            download = {
                downloads++
                flowOf(DownloadProgress(1, 1, 100.0, file = file))
            }
        }
        val updater = updater(engine)

        updater.checkNow(download = true)
        updater.checkNow(download = true)

        assertEquals(1, downloads)
    }

    @Test
    fun `a release withdrawn after downloading is forgotten`() = runTest {
        var result: UpdateResult = release()
        val file = artifact()
        val engine = FakeEngine { result }.serving(file)
        val updater = updater(engine)
        updater.checkNow(download = true)

        result = UpdateResult.NotAvailable
        updater.checkNow(download = true)

        assertEquals(AppUpdateState.UpToDate, updater.state.value)
        assertFalse(file.exists())
    }

    @Test
    fun `a scheduled check waits for the interval`() = runTest {
        val engine = FakeEngine { UpdateResult.NotAvailable }
        val updater = updater(engine)
        lastCheck.value = clock - 23 * HOUR

        updater.tick(24)
        assertEquals(0, engine.checks)

        clock += HOUR
        updater.tick(24)
        assertEquals(1, engine.checks)
    }

    @Test
    fun `a shorter interval makes a check due sooner`() = runTest {
        val engine = FakeEngine { UpdateResult.NotAvailable }
        val updater = updater(engine)
        lastCheck.value = clock - 2 * HOUR

        updater.tick(1)

        assertEquals(1, engine.checks)
    }

    @Test
    fun `a failed scheduled check is retried after an hour, not every tick`() = runTest {
        val engine = FakeEngine { UpdateResult.Error(NetworkException("offline")) }
        val updater = updater(engine)

        updater.tick(24)
        clock += HOUR - 1
        updater.tick(24)
        assertEquals(1, engine.checks)

        clock += 1
        updater.tick(24)
        assertEquals(2, engine.checks)
    }

    @Test
    fun `a clock moved back doesn't stop checks`() = runTest {
        val engine = FakeEngine { UpdateResult.NotAvailable }
        val updater = updater(engine)
        lastCheck.value = clock + 1000 * HOUR

        updater.tick(24)

        assertEquals(1, engine.checks)
    }

    @Test
    fun `a rejected release isn't downloaded again until the next interval`() = runTest {
        var downloads = 0
        val file = artifact()
        val engine = FakeEngine { release() }.apply {
            signature = Base64.getEncoder().encode(ByteArray(64))
            download = {
                downloads++
                flowOf(DownloadProgress(1, 1, 100.0, file = artifact()))
            }
        }
        val updater = updater(engine)

        updater.tick(24)
        clock += 2 * HOUR
        updater.tick(24)

        assertEquals(1, downloads)
        assertTrue(file.exists())
    }

    @Test
    fun `restarting locks the vault, then installs and relaunches`() = runTest {
        val file = artifact()
        val engine = FakeEngine { release() }.serving(file)
        val updater = updater(engine)
        updater.checkNow(download = true)

        updater.restartToInstall()

        engine.awaitInstall()
        assertEquals(listOf(file to true), engine.installs)
        coVerify { vaultManager.lock() }
    }

    @Test
    fun `restarting still installs if locking fails`() = runTest {
        coEvery { vaultManager.lock() } throws IOException("disk full")
        val engine = FakeEngine { release() }.serving(artifact())
        val updater = updater(engine)
        updater.checkNow(download = true)

        updater.restartToInstall()

        engine.awaitInstall()
        assertEquals(1, engine.installs.size)
    }

    @Test
    fun `an install that can't start says so`() = runTest {
        val engine = FakeEngine { release() }.serving(artifact()).apply { installFailure = IllegalStateException("no bundle") }
        val updater = updater(engine)
        updater.checkNow(download = true)

        updater.restartToInstall()

        assertIs<AppUpdateState.Failed>(updater.awaitState { it is AppUpdateState.Failed })
    }

    @Test
    fun `a downloaded file that has since been cleaned up isn't installed`() = runTest {
        val file = artifact()
        val engine = FakeEngine { release() }.serving(file)
        val updater = updater(engine)
        updater.checkNow(download = true)
        file.delete()

        updater.restartToInstall()

        updater.awaitState { it == AppUpdateState.Idle }
        assertTrue(engine.installs.isEmpty())
    }

    @Test
    fun `quitting installs without relaunching when automatic updates are on`() = runTest {
        val file = artifact()
        val engine = FakeEngine { release() }.serving(file)
        val updater = updater(engine)
        updater.checkNow(download = true)

        whileRunning(updater) { updater.installOnQuit() }

        assertEquals(listOf(file to false), engine.installs)
    }

    @Test
    fun `quitting installs nothing once automatic updates are turned off`() = runTest {
        val engine = FakeEngine { release() }.serving(artifact())
        val updater = updater(engine)
        updater.checkNow(download = true)

        whileRunning(updater) {
            autoUpdate.value = false
            delay(100)
            updater.installOnQuit()
        }

        assertTrue(engine.installs.isEmpty())
    }

    @Test
    fun `quitting still quits when the install can't start`() = runTest {
        val engine = FakeEngine { release() }.serving(artifact()).apply { installFailure = IOException("no") }
        val updater = updater(engine)
        updater.checkNow(download = true)

        whileRunning(updater) { updater.installOnQuit() }

        assertTrue(engine.installs.isEmpty())
    }

    @Test
    fun `open tabs and running forwards count as sessions, failed forwards don't`() = runTest {
        every { tabManager.openTabs() } returns listOf(mockk(), mockk())
        every { forwards.states } returns MutableStateFlow(
            mapOf(
                Uuid.random() to PortForwardState.Starting,
                Uuid.random() to PortForwardState.Failed("no"),
            ),
        )

        assertEquals(3, updater(FakeEngine { UpdateResult.NotAvailable }).openSessionCount())
    }

    @Test
    fun `builds follow the channel in their version`() {
        assertEquals("latest", updateChannelFor("1.2.3"))
        assertEquals("beta", updateChannelFor("1.2.3-beta.1"))
        assertEquals("alpha", updateChannelFor("1.2.3-ALPHA"))
        assertEquals("latest", updateChannelFor("1.2.3-rc.1"))
    }
}
