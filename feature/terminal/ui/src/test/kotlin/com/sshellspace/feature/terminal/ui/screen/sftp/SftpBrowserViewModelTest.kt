package com.sshellspace.feature.terminal.ui.screen.sftp

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.RemoteListing
import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.SftpSession
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.TransferProgress
import com.sshellspace.feature.terminal.domain.TransferSummary
import com.sshellspace.feature.terminal.domain.models.SftpParams
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class SftpBrowserViewModelTest {

    @TempDir
    lateinit var localDir: Path

    private val fakeSftp = FakeSftp()
    private val closeListeners = mutableListOf<() -> Unit>()
    private val ssh = mockk<ShellSession.Ssh>(relaxed = true) {
        every { openSftp() } returns fakeSftp
        every { onClose(any()) } answers { closeListeners += firstArg<() -> Unit>() }
    }
    private val connection = SshConnection(
        id = Uuid.random(),
        name = "server",
        host = "example.com",
        username = "alice",
        auth = SshConnectionAuth.Password("secret"),
    )
    private val activeVault = MutableStateFlow<VaultInfo?>(VaultInfo(Uuid.random(), "Work"))
    private val vaultManager = mockk<VaultManager> { every { activeVault } returns this@SftpBrowserViewModelTest.activeVault }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun file(name: String, directory: Boolean = false, size: Long = 0) = RemoteFile(
        name = name,
        path = "/home/alice/$name",
        kind = if (directory) RemoteFileKind.Directory else RemoteFileKind.File,
        size = size,
    )

    private class FakeSftp : SftpSession {
        var folders = mutableMapOf<String, List<RemoteFile>>()
        val existing = mutableSetOf<String>()
        val deleted = mutableListOf<RemoteFile>()
        val created = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        val downloads = mutableListOf<Triple<RemoteFile, Path, Boolean>>()
        val uploads = mutableListOf<Triple<Path, String, Boolean>>()
        var transferGate: CompletableDeferred<Unit>? = null
        var transferFailure: String? = null
        var closed = false

        override suspend fun home() = "/home/alice"
        override suspend fun resolve(path: String) =
            RemoteFile(path.substringAfterLast('/'), path, if (path in folders) RemoteFileKind.Directory else RemoteFileKind.File)
        override suspend fun list(path: String) =
            RemoteListing(folders[path] ?: throw SftpFailure("$path doesn't exist."))
        override suspend fun exists(path: String) = path in existing
        override suspend fun createDirectory(path: String) {
            created += path
        }
        override suspend fun rename(from: String, to: String) {
            renamed += from to to
        }
        override suspend fun delete(file: RemoteFile) {
            deleted += file
        }
        override suspend fun download(
            file: RemoteFile,
            target: Path,
            replace: Boolean,
            onProgress: (TransferProgress) -> Unit,
        ): TransferSummary {
            downloads += Triple(file, target, replace)
            onProgress(TransferProgress(1, 2, 0, 1))
            transferGate?.await()
            transferFailure?.let { throw SftpFailure(it) }
            return TransferSummary(listOf("escape: is a link"))
        }
        override suspend fun upload(
            source: Path,
            targetPath: String,
            replace: Boolean,
            onProgress: (TransferProgress) -> Unit,
        ): TransferSummary {
            uploads += Triple(source, targetPath, replace)
            transferGate?.await()
            return TransferSummary()
        }
        override fun close() {
            closed = true
        }
    }

    private val tabController = mockk<TabController>(relaxed = true)

    private fun service(connect: suspend (SshPrompter) -> SshConnectResult) = object : SshConnectionService {
        override suspend fun connect(connectionId: Uuid, prompter: SshPrompter) = connect(prompter)
    }

    private fun viewModel(
        service: SshConnectionService = service { SshConnectResult.Success(ssh, connection) },
    ) = SftpBrowserViewModel(tabController, SftpParams(connection.id, "server"), service, vaultManager)

    /** Waits for work that left the main dispatcher (`Dispatchers.IO`) to report back. */
    private suspend fun SftpBrowserViewModel.settled(condition: (SftpUiState) -> Boolean): SftpUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(condition) } }

    @Test
    fun `connecting lists the home folder and titles the tab`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("a.txt"))

        val vm = viewModel()
        val state = vm.settled { it.browser.path == "/home/alice" }

        assertEquals(SftpConnectionState.Connected, state.connection)
        assertEquals(listOf("a.txt"), state.browser.entries.map { it.name })
        verify { tabController.setTitle("server (files)") }
    }

    @Test
    fun `a failed connection is shown and can be retried`() = runTest {
        var result: SshConnectResult = SshConnectResult.Failed("refused")
        fakeSftp.folders["/home/alice"] = emptyList()
        val vm = viewModel(service { result })
        assertEquals(SftpConnectionState.Failed("refused"), vm.settled { it.connection is SftpConnectionState.Failed }.connection)

        result = SshConnectResult.Success(ssh, connection)
        vm.execute(SftpAction.Reconnect)

        vm.settled { it.connection == SftpConnectionState.Connected && it.browser.path == "/home/alice" }
    }

    @Test
    fun `a server without SFTP fails with its message and closes the session`() = runTest {
        every { ssh.openSftp() } throws SftpFailure("no sftp here")

        val vm = viewModel()

        assertEquals(SftpConnectionState.Failed("no sftp here"), vm.settled { it.connection is SftpConnectionState.Failed }.connection)
        verify { ssh.close() }
    }

    @Test
    fun `host key questions go through the state and wait for the answer`() = runTest {
        val prompt = HostKeyPrompt("example.com", 22, "ed25519", "SHA256:abc")
        val answer = CompletableDeferred<Boolean>()
        val vm = viewModel(
            service { prompter ->
                answer.complete(prompter.confirmHostKey(prompt))
                SshConnectResult.Cancelled
            },
        )
        assertEquals(SftpConnectionState.AwaitingHostKey(prompt), vm.uiState.value.connection)

        vm.execute(SftpAction.AnswerHostKey(trust = true))

        assertTrue(answer.await())
        assertEquals(SftpConnectionState.Cancelled, vm.uiState.value.connection)
    }

    @Test
    fun `a dropped session shows disconnected and reconnects to the same folder`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("logs", directory = true))
        fakeSftp.folders["/home/alice/logs"] = listOf(file("x.log"))
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        vm.execute(SftpAction.Navigate("/home/alice/logs"))
        vm.settled { it.browser.path == "/home/alice/logs" }

        closeListeners.last().invoke()

        assertIs<SftpConnectionState.Disconnected>(vm.settled { it.connection is SftpConnectionState.Disconnected }.connection)
        assertTrue(fakeSftp.closed)

        vm.execute(SftpAction.Reconnect)
        val state = vm.settled { it.connection == SftpConnectionState.Connected && !it.browser.isLoading }
        assertEquals("/home/alice/logs", state.browser.path)
    }

    @Test
    fun `a failed navigation stays in the folder and says why`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("a.txt"))
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Navigate("/gone"))

        val state = vm.settled { it.browser.notice != null }
        assertEquals("/gone doesn't exist.", state.browser.notice)
        assertEquals("/home/alice", state.browser.path)
    }

    @Test
    fun `dotfiles stay hidden until asked for, and folders sort first`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("z.txt"), file(".env"), file("apps", directory = true), file("b.txt"))
        val vm = viewModel()
        val state = vm.settled { it.browser.path == "/home/alice" }.browser

        assertEquals(listOf("apps", "b.txt", "z.txt"), state.visibleEntries().map { it.name })
        assertEquals(listOf("apps", ".env", "b.txt", "z.txt"), state.copy(showHidden = true).visibleEntries().map { it.name })
        assertEquals(
            listOf("apps", "z.txt", "b.txt"),
            state.copy(sortAscending = false).visibleEntries().map { it.name },
        )
    }

    @Test
    fun `clicks select one, toggle and select a range`() = runTest {
        val files = listOf(file("a"), file("b"), file("c"), file("d"))
        fakeSftp.folders["/home/alice"] = files
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        val selectedNames = { vm.uiState.value.browser.selectedFiles().map { it.name } }

        vm.execute(SftpAction.Click(files[1], SftpClickMode.Single))
        assertEquals(listOf("b"), selectedNames())
        vm.execute(SftpAction.Click(files[3], SftpClickMode.Toggle))
        assertEquals(listOf("b", "d"), selectedNames())
        vm.execute(SftpAction.Click(files[3], SftpClickMode.Single))
        vm.execute(SftpAction.Click(files[1], SftpClickMode.Range))
        assertEquals(listOf("b", "c", "d"), selectedNames())
        vm.execute(SftpAction.SelectAll)
        assertEquals(listOf("a", "b", "c", "d"), selectedNames())
    }

    @Test
    fun `a hidden selected file is left out of a delete`() = runTest {
        val hidden = file(".env")
        val shown = file("a.txt")
        fakeSftp.folders["/home/alice"] = listOf(hidden, shown)
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        vm.execute(SftpAction.ToggleHidden)
        vm.execute(SftpAction.SelectAll)
        vm.execute(SftpAction.ToggleHidden)

        vm.execute(SftpAction.RequestDelete)

        assertEquals(listOf(shown), assertIs<SftpDialog.ConfirmDelete>(vm.uiState.value.browser.dialog).files)
    }

    @Test
    fun `new folder rejects names that leave the folder and creates good ones`() = runTest {
        fakeSftp.folders["/home/alice"] = emptyList()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.RequestNewFolder)
        vm.execute(SftpAction.CreateFolder("../escape"))
        assertTrue(assertIs<SftpDialog.NewFolder>(vm.uiState.value.browser.dialog).error != null)
        assertEquals(emptyList(), fakeSftp.created)

        vm.execute(SftpAction.CreateFolder(" photos "))
        vm.settled { it.browser.busyMessage == null && !it.browser.isLoading }
        assertEquals(listOf("/home/alice/photos"), fakeSftp.created)
        assertNull(vm.uiState.value.browser.dialog)
    }

    @Test
    fun `rename refuses to overwrite what is already there`() = runTest {
        val a = file("a.txt")
        fakeSftp.folders["/home/alice"] = listOf(a, file("b.txt"))
        fakeSftp.existing += "/home/alice/b.txt"
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Rename(a, "b.txt"))

        assertEquals("b.txt already exists.", vm.settled { it.browser.notice != null }.browser.notice)
        assertEquals(emptyList(), fakeSftp.renamed)

        vm.execute(SftpAction.Rename(a, "c.txt"))
        vm.settled { fakeSftp.renamed.isNotEmpty() }
        assertEquals(listOf("/home/alice/a.txt" to "/home/alice/c.txt"), fakeSftp.renamed)
    }

    @Test
    fun `a download with no conflict runs and reports what it skipped`() = runTest {
        val a = file("a.txt")
        fakeSftp.folders["/home/alice"] = listOf(a)
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Download(listOf(a), localDir))

        val transfer = vm.settled { it.transfers.firstOrNull()?.status == SftpTransferStatus.Done }.transfers.single()
        assertEquals(Triple(a, localDir.resolve("a.txt"), false), fakeSftp.downloads.single())
        assertEquals(listOf("escape: is a link"), transfer.skipped)
    }

    @Test
    fun `a download over an existing file asks first, and keep both picks a free name`() = runTest {
        val a = file("a.txt")
        fakeSftp.folders["/home/alice"] = listOf(a)
        Files.writeString(localDir.resolve("a.txt"), "mine")
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Download(listOf(a), localDir))

        assertEquals(listOf("a.txt"), assertIs<SftpDialog.Conflict>(vm.settled { it.browser.dialog != null }.browser.dialog).names)
        assertEquals(emptyList(), fakeSftp.downloads)

        vm.execute(SftpAction.ResolveConflict(SftpConflictChoice.KeepBoth))
        vm.settled { it.transfers.firstOrNull()?.status == SftpTransferStatus.Done }
        assertEquals(Triple(a, localDir.resolve("a (1).txt"), false), fakeSftp.downloads.single())
    }

    @Test
    fun `choosing replace overwrites`() = runTest {
        val a = file("a.txt")
        fakeSftp.folders["/home/alice"] = listOf(a)
        Files.writeString(localDir.resolve("a.txt"), "mine")
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        vm.execute(SftpAction.Download(listOf(a), localDir))
        vm.settled { it.browser.dialog != null }

        vm.execute(SftpAction.ResolveConflict(SftpConflictChoice.Replace))

        vm.settled { it.transfers.firstOrNull()?.status == SftpTransferStatus.Done }
        assertEquals(Triple(a, localDir.resolve("a.txt"), true), fakeSftp.downloads.single())
    }

    @Test
    fun `a file whose name is unsafe here is not downloaded`() = runTest {
        val evil = file("..\\..\\evil")
        fakeSftp.folders["/home/alice"] = listOf(evil)
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Download(listOf(evil), localDir))

        assertTrue(vm.settled { it.browser.notice != null }.browser.notice!!.contains("isn't safe"))
        assertEquals(emptyList(), fakeSftp.downloads)
    }

    @Test
    fun `an upload goes to the folder on show and refreshes it`() = runTest {
        fakeSftp.folders["/home/alice"] = emptyList()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        val source = localDir.resolve("up.txt")
        fakeSftp.folders["/home/alice"] = listOf(file("up.txt"))

        vm.execute(SftpAction.Upload(listOf(source)))

        val state = vm.settled { it.transfers.firstOrNull()?.status == SftpTransferStatus.Done && it.browser.entries.isNotEmpty() }
        assertEquals(Triple(source, "/home/alice/up.txt", false), fakeSftp.uploads.single())
        assertEquals(listOf("up.txt"), state.browser.entries.map { it.name })
    }

    @Test
    fun `several dropped files each upload to the folder on show, and asks before replacing`() = runTest {
        fakeSftp.folders["/home/alice"] = emptyList()
        fakeSftp.folders["/home/alice/logs"] = emptyList()
        fakeSftp.existing += "/home/alice/logs/taken.txt"
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        vm.execute(SftpAction.Navigate("/home/alice/logs"))
        vm.settled { it.browser.path == "/home/alice/logs" }
        val free = localDir.resolve("free.txt")
        val taken = localDir.resolve("taken.txt")

        vm.execute(SftpAction.Upload(listOf(free, taken)))

        assertEquals(listOf("taken.txt"), assertIs<SftpDialog.Conflict>(vm.settled { it.browser.dialog != null }.browser.dialog).names)
        assertEquals(emptyList(), fakeSftp.uploads)

        vm.execute(SftpAction.ResolveConflict(SftpConflictChoice.KeepBoth))

        vm.settled { state -> state.transfers.size == 2 && state.transfers.all { it.status == SftpTransferStatus.Done } }
        assertEquals(
            setOf(Triple(free, "/home/alice/logs/free.txt", false), Triple(taken, "/home/alice/logs/taken (1).txt", false)),
            fakeSftp.uploads.toSet(),
        )
    }

    @Test
    fun `a failed transfer shows the reason, and a cancelled one stays cancelled`() = runTest {
        val a = file("a.txt")
        fakeSftp.folders["/home/alice"] = listOf(a)
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        fakeSftp.transferFailure = "disk full"
        vm.execute(SftpAction.Download(listOf(a), localDir))
        val failed = vm.settled { it.transfers.firstOrNull()?.status?.isFinished == true }.transfers.single()
        assertEquals(SftpTransferStatus.Failed("disk full"), failed.status)

        vm.execute(SftpAction.ClearFinishedTransfers)
        assertEquals(emptyList(), vm.uiState.value.transfers)

        fakeSftp.transferFailure = null
        fakeSftp.transferGate = CompletableDeferred()
        vm.execute(SftpAction.Download(listOf(a), localDir))
        val running = vm.settled { it.transfers.singleOrNull()?.status == SftpTransferStatus.Running }.transfers.single()

        vm.execute(SftpAction.CancelTransfer(running.id))

        assertEquals(SftpTransferStatus.Cancelled, vm.uiState.value.transfers.single().status)
    }

    @Test
    fun `at most three transfers run at once`() = runTest {
        val files = (1..5).map { file("f$it") }
        fakeSftp.folders["/home/alice"] = files
        fakeSftp.transferGate = CompletableDeferred()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Download(files, localDir))

        val state = vm.settled { it.transfers.size == 5 && it.transfers.count { t -> t.status == SftpTransferStatus.Running } == 3 }
        assertEquals(2, state.transfers.count { it.status == SftpTransferStatus.Queued })
        fakeSftp.transferGate!!.complete(Unit)
        vm.settled { it.transfers.all { t -> t.status == SftpTransferStatus.Done } }
    }

    @Test
    fun `status text and formats read well`() {
        assertEquals("512 B", formatSize(512))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("2.0 GB", formatSize(2L * 1024 * 1024 * 1024))
        assertEquals("rwxr-xr--", formatMode("754".toInt(8)))
        assertEquals("", formatMode(null))
        assertEquals(
            listOf("/" to "/", "var" to "/var", "www" to "/var/www"),
            pathSegments("/var/www"),
        )
        val running = SftpTransfer(1, SftpDirection.Upload, "x", SftpTransferStatus.Running, TransferProgress(1024, 2048, 1, 3))
        assertEquals("1.0 KB of 2.0 KB, 1 of 3 files", running.statusText())
        assertEquals(0.5f, running.fraction())
    }
}
