package com.sshellspace.feature.terminal.ui.screen.sftp

import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.designsystem.Icons
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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
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
        val contents = mutableMapOf<String, ByteArray>()
        val reads = mutableListOf<String>()
        var readGate: CompletableDeferred<Unit>? = null
        var readFailure: String? = null

        override suspend fun home() = "/home/alice"
        override suspend fun resolve(path: String) =
            RemoteFile(path.substringAfterLast('/'), path, if (path in folders) RemoteFileKind.Directory else RemoteFileKind.File)
        override suspend fun list(path: String) =
            RemoteListing(folders[path] ?: throw SftpFailure("$path doesn't exist."))
        override suspend fun exists(path: String) = path in existing
        override suspend fun readBytes(file: RemoteFile, maxBytes: Long): ByteArray {
            reads += file.name
            readGate?.await()
            readFailure?.let { throw SftpFailure(it) }
            return contents[file.name] ?: testPng(2, 3)
        }
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
    ) = SftpBrowserViewModel(
        tabController,
        SftpParams(connection.id, "server"),
        service,
        vaultManager,
        { error("A saved connection never opens this computer's files.") },
    )

    /**
     * The selected file names, once [expected] shows up. The state is built on another thread, so a read right
     * after an action can still be the one before it.
     */
    private suspend fun SftpBrowserViewModel.selectedNames(expected: List<String>): List<String> {
        val names = { state: SftpUiState -> state.browser.selectedFiles().map { it.name } }
        withContext(Dispatchers.Default) { withTimeoutOrNull(5_000) { uiState.first { names(it) == expected } } }
        return names(uiState.value)
    }

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
        assertEquals(SftpConnectionState.AwaitingHostKey(prompt), vm.settled { it.connection == SftpConnectionState.AwaitingHostKey(prompt) }.connection)

        vm.execute(SftpAction.AnswerHostKey(trust = true))

        assertTrue(answer.await())
        assertEquals(SftpConnectionState.Cancelled, vm.settled { it.connection == SftpConnectionState.Cancelled }.connection)
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

        vm.execute(SftpAction.Click(files[1], SftpClickMode.Single))
        assertEquals(listOf("b"), vm.selectedNames(listOf("b")))
        vm.execute(SftpAction.Click(files[3], SftpClickMode.Toggle))
        assertEquals(listOf("b", "d"), vm.selectedNames(listOf("b", "d")))
        vm.execute(SftpAction.Click(files[3], SftpClickMode.Single))
        vm.execute(SftpAction.Click(files[1], SftpClickMode.Range))
        assertEquals(listOf("b", "c", "d"), vm.selectedNames(listOf("b", "c", "d")))
        vm.execute(SftpAction.SelectAll)
        assertEquals(listOf("a", "b", "c", "d"), vm.selectedNames(listOf("a", "b", "c", "d")))
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

        assertEquals(listOf(shown), assertIs<SftpDialog.ConfirmDelete>(vm.settled { it.browser.dialog is SftpDialog.ConfirmDelete }.browser.dialog).files)
    }

    @Test
    fun `new folder rejects names that leave the folder and creates good ones`() = runTest {
        fakeSftp.folders["/home/alice"] = emptyList()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.RequestNewFolder)
        vm.execute(SftpAction.CreateFolder("../escape"))
        assertTrue(assertIs<SftpDialog.NewFolder>(vm.settled { (it.browser.dialog as? SftpDialog.NewFolder)?.error != null }.browser.dialog).error != null)
        assertEquals(emptyList(), fakeSftp.created)

        vm.execute(SftpAction.CreateFolder(" photos "))
        vm.settled { it.browser.busyMessage == null && !it.browser.isLoading && it.browser.dialog == null }
        assertEquals(listOf("/home/alice/photos"), fakeSftp.created)
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
        assertEquals(emptyList(), vm.settled { it.transfers.isEmpty() }.transfers)

        fakeSftp.transferFailure = null
        fakeSftp.transferGate = CompletableDeferred()
        vm.execute(SftpAction.Download(listOf(a), localDir))
        val running = vm.settled { it.transfers.singleOrNull()?.status == SftpTransferStatus.Running }.transfers.single()

        vm.execute(SftpAction.CancelTransfer(running.id))

        assertEquals(SftpTransferStatus.Cancelled, vm.settled { it.transfers.singleOrNull()?.status == SftpTransferStatus.Cancelled }.transfers.single().status)
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
    fun `a name listed twice only makes one row`() = runTest {
        // The list can't show two rows for one path, so this would take down the app.
        fakeSftp.folders["/home/alice"] = listOf(file("a"), file("a"), file("b"))
        val state = viewModel().settled { it.browser.path == "/home/alice" }.browser

        assertEquals(listOf("a", "b"), state.visibleEntries().map { it.name })
    }

    @Test
    fun `the filter narrows the list and keeps what it hides out of a delete, until the folder changes`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("notes.txt"), file("NOTES.md"), file("z.log"))
        fakeSftp.folders["/home/alice/sub"] = emptyList()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.SelectAll)
        vm.execute(SftpAction.SetFilter("note"))
        val filtered = vm.settled { it.browser.filter == "note" && it.browser.selection.size == 3 }.browser
        assertEquals(listOf("NOTES.md", "notes.txt"), filtered.visibleEntries().map { it.name })
        assertEquals(listOf("NOTES.md", "notes.txt"), filtered.selectedFiles().map { it.name })
        vm.execute(SftpAction.RequestDelete)
        val confirm = assertIs<SftpDialog.ConfirmDelete>(vm.settled { it.browser.dialog is SftpDialog.ConfirmDelete }.browser.dialog)
        assertEquals(listOf("NOTES.md", "notes.txt"), confirm.files.map { it.name })
        vm.execute(SftpAction.DismissDialog)

        vm.execute(SftpAction.Refresh)
        assertEquals("note", vm.settled { !it.browser.isLoading }.browser.filter)
        vm.execute(SftpAction.Navigate("/home/alice/sub"))
        assertEquals("", vm.settled { it.browser.path == "/home/alice/sub" }.browser.filter)
    }

    @Test
    fun `moving the selection walks the list, extends with shift and stops at the ends`() = runTest {
        val files = listOf(file("a"), file("b"), file("c"), file("d"))
        fakeSftp.folders["/home/alice"] = files
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Click(files[1], SftpClickMode.Single))
        assertEquals(false, vm.settled { !it.browser.revealCursor }.browser.revealCursor)
        vm.execute(SftpAction.MoveSelection(1, extend = true))
        assertEquals(listOf("b", "c"), vm.selectedNames(listOf("b", "c")))
        assertEquals(true, vm.settled { it.browser.revealCursor }.browser.revealCursor)
        vm.execute(SftpAction.MoveSelection(1, extend = true))
        assertEquals(listOf("b", "c", "d"), vm.selectedNames(listOf("b", "c", "d")))
        vm.execute(SftpAction.MoveSelection(-1, extend = true))
        assertEquals(listOf("b", "c"), vm.selectedNames(listOf("b", "c")))
        vm.execute(SftpAction.MoveSelection(-1))
        assertEquals(listOf("b"), vm.selectedNames(listOf("b")))
        vm.execute(SftpAction.MoveSelection(-10))
        assertEquals(listOf("a"), vm.selectedNames(listOf("a")))
        vm.execute(SftpAction.MoveSelection(10))
        assertEquals(listOf("d"), vm.selectedNames(listOf("d")))
        vm.execute(SftpAction.ClearSelection)
        assertEquals(emptyList(), vm.selectedNames(emptyList()))
    }

    @Test
    fun `with nothing current, a move counts from the end of the list it is leaving`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("a"), file("b"), file("c"), file("d"))
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        suspend fun after(action: SftpAction, expected: String): List<String> {
            vm.execute(SftpAction.ClearSelection)
            vm.execute(action)
            return vm.selectedNames(listOf(expected))
        }

        assertEquals(listOf("a"), after(SftpAction.MoveSelection(0), "a"))
        assertEquals(listOf("a"), after(SftpAction.MoveSelection(1), "a"))
        assertEquals(listOf("d"), after(SftpAction.MoveSelection(-1), "d"))
        // What Home and End send: the length of the list.
        assertEquals(listOf("d"), after(SftpAction.MoveSelection(4), "d"))
        assertEquals(listOf("a"), after(SftpAction.MoveSelection(-4), "a"))
    }

    @Test
    fun `going up lands on the folder it left`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("sub", directory = true), file("x"))
        fakeSftp.folders["/home/alice/sub"] = emptyList()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        vm.execute(SftpAction.Navigate("/home/alice/sub"))
        vm.settled { it.browser.path == "/home/alice/sub" }

        vm.execute(SftpAction.GoUp)
        val state = vm.settled { it.browser.path == "/home/alice" }.browser

        assertEquals(setOf("/home/alice/sub"), state.selection)
        assertEquals("/home/alice/sub", state.selectionCursor)
        assertEquals(true, state.revealCursor)
    }

    @Test
    fun `the status bar counts what is on show`() {
        val entries = listOf(file("a", size = 100), file("sub", directory = true), file(".env"))
        val state = SftpBrowserState(entries = entries)
        val summary = { current: SftpBrowserState, selected: List<RemoteFile> ->
            current.summary(current.visibleEntries(), selected)
        }

        assertEquals("2 items, 1 hidden", summary(state, emptyList()))
        assertEquals("3 items", summary(state.copy(showHidden = true), emptyList()))
        assertEquals("1 of 2 items", summary(state.copy(filter = "A"), emptyList()))
        assertEquals("1 of 2 selected, 100 B", summary(state, listOf(entries[0])))
        // The size a server gives a folder means nothing, so it isn't called 0 B either.
        assertEquals("1 of 2 selected", summary(state, listOf(entries[1])))
    }

    @Test
    fun `the transfers header sums up what is under way`() {
        val running = { done: Long -> SftpTransfer(1, SftpDirection.Upload, "x", SftpTransferStatus.Running, TransferProgress(done, 2048, 0, 1)) }
        val queued = SftpTransfer(2, SftpDirection.Upload, "y")
        val failed = SftpTransfer(3, SftpDirection.Upload, "z", SftpTransferStatus.Failed("no"))
        val done = SftpTransfer(4, SftpDirection.Upload, "w", SftpTransferStatus.Done)

        assertEquals("2 running, 1 waiting", transfersSummary(listOf(running(0), running(0), queued, failed)))
        assertEquals("All finished", transfersSummary(listOf(done, failed)))
        assertEquals(0.25f, transfersFraction(listOf(running(1024), running(0), queued, failed)))
        assertNull(transfersFraction(listOf(queued, done)))
    }

    @Test
    fun `rows get an icon from their kind, name and mode`() {
        val named = { name: String, mode: String -> file(name).copy(permissions = mode.toInt(8)) }

        assertEquals(Icons.Folder, fileIcon(file("backup.tar.gz", directory = true)))
        assertEquals(Icons.Link, fileIcon(file("current", directory = true).copy(isSymlink = true)))
        assertEquals(Icons.Archive, fileIcon(named("backup.tar.gz", "644")))
        assertEquals(Icons.Image, fileIcon(named("PHOTO.PNG", "644")))
        assertEquals(Icons.Code, fileIcon(named("gradlew", "755")))
        assertEquals(Icons.File, fileIcon(named("gradlew", "644")))
    }

    @Test
    fun `this computer opens with no SSH, no vault and no questions`() = runTest {
        fakeSftp.folders["/home/alice"] = listOf(file("notes.txt"))
        val failsIfUsed = service { error("Browsing this computer must not touch SSH.") }
        val lockedVault = mockk<VaultManager> { every { activeVault } returns MutableStateFlow(null) }

        val vm = SftpBrowserViewModel(tabController, SftpParams.ThisDevice, failsIfUsed, lockedVault, { fakeSftp })
        val state = vm.settled { it.browser.path == "/home/alice" }

        assertEquals(SftpConnectionState.Connected, state.connection)
        assertEquals(true, state.browser.isThisDevice)
        assertEquals(listOf("notes.txt"), state.browser.visibleEntries().map { it.name })
    }

    @Test
    fun `a name that only Windows objects to can still be copied when it is already on this computer`() = runTest {
        val odd = file("a:b.txt")
        fakeSftp.folders["/home/alice"] = listOf(odd)
        val vm = SftpBrowserViewModel(tabController, SftpParams.ThisDevice, service { error("unused") }, vaultManager, { fakeSftp })
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.Download(listOf(odd), localDir))

        assertEquals(listOf(odd), vm.settled { it.transfers.any { t -> t.status.isFinished } }.let { fakeSftp.downloads.map { d -> d.first } })
    }

    @Test
    fun `a picture opens, goes through the others on show, and stops at the ends`() = runTest {
        val pictures = listOf(file("a.png"), file("b.txt"), file("c.JPG"), file("d.webp"))
        fakeSftp.folders["/home/alice"] = pictures
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }
        val shown = { vm.uiState.value.browser.dialog as? SftpDialog.Preview }
        suspend fun SftpBrowserViewModel.loaded(name: String) = settled {
            (it.browser.dialog as? SftpDialog.Preview)?.let { p -> p.file.name == name && p.image != null } == true
        }

        vm.execute(SftpAction.OpenPreview(pictures[0]))
        vm.loaded("a.png")
        assertEquals(2, shown()?.image?.width)
        assertEquals(3, shown()?.image?.height)

        vm.execute(SftpAction.MovePreview(-1))
        assertEquals("a.png", shown()?.file?.name)
        vm.execute(SftpAction.MovePreview(1))
        vm.loaded("c.JPG")
        assertEquals(setOf("/home/alice/c.JPG"), vm.uiState.value.browser.selection)
        vm.execute(SftpAction.MovePreview(1))
        vm.loaded("d.webp")
        vm.execute(SftpAction.MovePreview(1))
        assertEquals("d.webp", shown()?.file?.name)
        assertEquals(listOf("a.png", "c.JPG", "d.webp"), fakeSftp.reads)

        vm.execute(SftpAction.DismissDialog)
        assertNull(vm.settled { it.browser.dialog == null }.browser.dialog)
    }

    @Test
    fun `a picture that arrives after the preview closed stays closed`() = runTest {
        val picture = file("a.png")
        fakeSftp.folders["/home/alice"] = listOf(picture)
        fakeSftp.readGate = CompletableDeferred()
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.OpenPreview(picture))
        assertNull((vm.settled { it.browser.dialog is SftpDialog.Preview }.browser.dialog as SftpDialog.Preview).image)
        vm.execute(SftpAction.DismissDialog)
        fakeSftp.readGate!!.complete(Unit)

        assertNull(vm.settled { it.browser.dialog == null }.browser.dialog)
    }

    @Test
    fun `a preview that can't load says why instead of closing`() = runTest {
        val picture = file("a.png")
        fakeSftp.folders["/home/alice"] = listOf(picture)
        fakeSftp.readFailure = "a.png is too large to open here."
        val vm = viewModel()
        vm.settled { it.browser.path == "/home/alice" }

        vm.execute(SftpAction.OpenPreview(picture))

        val state = vm.settled { (it.browser.dialog as? SftpDialog.Preview)?.error != null }
        assertEquals("a.png is too large to open here.", (state.browser.dialog as SftpDialog.Preview).error)
    }

    @Test
    fun `decoding refuses what isn't a picture, and a huge one before it takes the memory`() {
        val decoded = decodePreview(testPng(5, 7))
        assertEquals(5, decoded.width)
        assertEquals(7, decoded.height)

        assertEquals("This file can't be read as a picture.", assertFailsWith<SftpFailure> { decodePreview(ByteArray(64)) }.message)
        // A few dozen bytes that claim 60000 × 60000, which would need 14 GB to decode.
        val bomb = assertFailsWith<SftpFailure> { decodePreview(pngClaiming(60_000, 60_000)) }
        assertEquals("This picture is too large to preview (60000 × 60000).", bomb.message)
    }

    @Test
    fun `decoding shrinks a very big picture but reports its real size`() {
        val decoded = decodePreview(testPng(5000, 100))
        assertEquals(5000, decoded.width)
        assertEquals(4096, decoded.bitmap.width)
    }

    @Test
    fun `only formats the decoder reads can be previewed`() {
        assertTrue(file("a.PNG").canPreview)
        assertTrue(file("photo.jpeg").canPreview)
        assertEquals(false, file("drawing.svg").canPreview)
        assertEquals(false, file("notes.txt").canPreview)
        assertEquals(false, file("folder.png", directory = true).canPreview)
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

private fun testPng(width: Int, height: Int): ByteArray =
    Surface.makeRasterN32Premul(width, height).use { surface ->
        surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG)!!.bytes }
    }

/** A well-formed PNG of a few dozen bytes that claims to be [width] by [height], with almost no picture behind that. */
private fun pngClaiming(width: Int, height: Int): ByteArray {
    fun chunk(type: String, data: ByteArray): ByteArray {
        val body = type.toByteArray() + data
        val checksum = CRC32().apply { update(body) }.value.toInt()
        return ByteBuffer.allocate(4 + body.size + 4).putInt(data.size).put(body).putInt(checksum).array()
    }
    val header = ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(6).put(0).put(0).put(0).array()
    val pixels = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(ByteArray(16)) } }.toByteArray()
    return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
        chunk("IHDR", header) + chunk("IDAT", pixels) + chunk("IEND", ByteArray(0))
}
