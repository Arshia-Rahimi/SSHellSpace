package com.sshellspace.feature.terminal.data.sftp

import com.sshellspace.core.data.SshConnectionDatasource
import com.sshellspace.core.data.SshKeyDatasource
import com.sshellspace.core.domain.SshKeyService
import com.sshellspace.core.domain.models.SshConnection
import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import com.sshellspace.feature.terminal.data.MinaSshConnectionService
import com.sshellspace.feature.terminal.data.createSshClient
import com.sshellspace.feature.terminal.data.ssh.agent.SshAgentLocator
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.SftpSession
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.TransferProgress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

/** Runs real file operations against an in-process MINA SFTP server rooted in a temp folder. */
class MinaSftpSessionTest {

    private val tempDir: Path = Files.createTempDirectory("sshellspace-sftp-test")
    private val remoteRoot: Path = Files.createDirectory(tempDir.resolve("remote"))
    private val localRoot: Path = Files.createDirectory(tempDir.resolve("local"))

    private val server = SshServer.setUpDefaultServer().apply {
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(tempDir.resolve("host.key"))
        passwordAuthenticator = PasswordAuthenticator { user, password, _ -> user == "alice" && password == "secret" }
        subsystemFactories = listOf(SftpSubsystemFactory())
        fileSystemFactory = VirtualFileSystemFactory(remoteRoot)
        start()
    }

    private val client = createSshClient(tempDir.resolve("known_hosts"), SshAgentLocator { null })
    private val ssh: ShellSession.Ssh = runBlocking { connect() }
    private val sftp: SftpSession = ssh.openSftp()

    @AfterTest
    fun tearDown() {
        sftp.close()
        ssh.close()
        client.stop()
        server.stop(true)
        tempDir.toFile().deleteRecursively()
    }

    private suspend fun connect(): ShellSession.Ssh {
        val connection = SshConnection(
            id = Uuid.random(),
            name = "test server",
            host = "127.0.0.1",
            port = server.port,
            username = "alice",
            auth = SshConnectionAuth.Password("secret"),
            options = SshConnectionOptions(connectTimeoutSeconds = 5),
        )
        val datasource = mockk<SshConnectionDatasource> {
            coEvery { get(connection.id) } returns connection
        }
        val service = MinaSshConnectionService(
            client,
            datasource,
            mockk<SshKeyDatasource>(),
            mockk<SshKeyService>(),
            SshAgentLocator { null },
        )
        return assertIs<SshConnectResult.Success>(service.connect(connection.id, TrustingPrompter)).session
    }

    private object TrustingPrompter : SshPrompter {
        override suspend fun confirmHostKey(prompt: HostKeyPrompt) = true
        override suspend fun askSecret(prompt: SecretPrompt): String? = null
        override suspend fun answerChallenge(challenge: AuthChallenge): List<String>? = null
    }

    private fun remoteFile(relative: String, content: String = "content"): Path =
        remoteRoot.resolve(relative).also {
            Files.createDirectories(it.parent)
            Files.writeString(it, content)
        }

    private suspend fun entry(path: String) = sftp.list(path.substringBeforeLast('/').ifEmpty { "/" })
        .files.single { it.path == path }

    @Test
    fun `lists a folder with kinds, sizes and links`() = runBlocking {
        remoteFile("docs/a.txt", "12345")
        Files.createDirectory(remoteRoot.resolve("docs/sub"))
        Files.createSymbolicLink(remoteRoot.resolve("docs/to-sub"), remoteRoot.resolve("docs/sub"))
        Files.createSymbolicLink(remoteRoot.resolve("docs/broken"), remoteRoot.resolve("docs/nothing"))

        val files = sftp.list("/docs").files.associateBy { it.name }

        assertEquals(setOf("a.txt", "sub", "to-sub", "broken"), files.keys)
        assertEquals(RemoteFileKind.File, files.getValue("a.txt").kind)
        assertEquals(5L, files.getValue("a.txt").size)
        assertEquals(RemoteFileKind.Directory, files.getValue("sub").kind)
        assertTrue(files.getValue("to-sub").isDirectory && files.getValue("to-sub").isSymlink)
        assertEquals(RemoteFileKind.Other, files.getValue("broken").kind)
    }

    @Test
    fun `listing a missing folder fails with a readable message`() = runBlocking {
        val failure = assertFailsWith<SftpFailure> { sftp.list("/missing") }
        assertEquals("/missing doesn't exist.", failure.message)
    }

    @Test
    fun `creates, renames and deletes`() = runBlocking {
        sftp.createDirectory("/new")
        assertTrue(Files.isDirectory(remoteRoot.resolve("new")))
        assertTrue(sftp.exists("/new"))

        sftp.rename("/new", "/renamed")
        assertFalse(sftp.exists("/new"))

        sftp.delete(sftp.resolve("/renamed"))
        assertFalse(Files.exists(remoteRoot.resolve("renamed")))
    }

    @Test
    fun `deleting a folder removes its contents but not what a link inside points to`() = runBlocking {
        val outside = remoteFile("keep/precious.txt")
        remoteFile("doomed/a/b/c.txt")
        Files.createSymbolicLink(remoteRoot.resolve("doomed/link"), remoteRoot.resolve("keep"))

        sftp.delete(entry("/doomed"))

        assertFalse(Files.exists(remoteRoot.resolve("doomed")))
        assertTrue(Files.exists(outside))
    }

    @Test
    fun `deleting a link to a folder removes only the link`() = runBlocking {
        val outside = remoteFile("keep/precious.txt")
        Files.createSymbolicLink(remoteRoot.resolve("link"), remoteRoot.resolve("keep"))

        sftp.delete(entry("/link"))

        assertFalse(Files.exists(remoteRoot.resolve("link"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(outside))
    }

    @Test
    fun `downloads a file and reports progress`() = runBlocking {
        val data = "x".repeat(300_000)
        remoteFile("big.bin", data)
        val updates = mutableListOf<TransferProgress>()

        sftp.download(entry("/big.bin"), localRoot.resolve("big.bin"), replace = false) { updates += it }

        assertEquals(data, Files.readString(localRoot.resolve("big.bin")))
        assertEquals(TransferProgress(300_000, 300_000, 1, 1), updates.last())
        assertEquals(listOf("big.bin"), Files.list(localRoot).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `downloading refuses to overwrite unless told to`() = runBlocking {
        remoteFile("a.txt", "new")
        val target = localRoot.resolve("a.txt")
        Files.writeString(target, "old")

        assertFailsWith<SftpFailure> { sftp.download(entry("/a.txt"), target, replace = false) {} }
        assertEquals("old", Files.readString(target))

        sftp.download(entry("/a.txt"), target, replace = true) {}
        assertEquals("new", Files.readString(target))
    }

    @Test
    fun `downloads a folder and skips links inside it`() = runBlocking {
        remoteFile("tree/a.txt", "a")
        remoteFile("tree/sub/b.txt", "b")
        remoteFile("secret/key", "hidden")
        Files.createSymbolicLink(remoteRoot.resolve("tree/escape"), remoteRoot.resolve("secret"))

        val summary = sftp.download(entry("/tree"), localRoot.resolve("tree"), replace = false) {}

        assertEquals("a", Files.readString(localRoot.resolve("tree/a.txt")))
        assertEquals("b", Files.readString(localRoot.resolve("tree/sub/b.txt")))
        assertFalse(Files.exists(localRoot.resolve("tree/escape"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals(listOf("escape: is a link"), summary.skipped)
    }

    @Test
    fun `a cancelled download leaves nothing behind`() = runBlocking {
        remoteFile("big.bin", "x".repeat(1_000_000))
        val target = localRoot.resolve("big.bin")

        assertFailsWith<CancellationException> {
            sftp.download(entry("/big.bin"), target, replace = false) { progress ->
                if (progress.bytesDone > 0) throw CancellationException("stop")
                Thread.sleep(150)
            }
        }

        assertEquals(0, Files.list(localRoot).use { it.count() }.toInt())
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `a downloaded file stays private and loses write access for others`() = runBlocking {
        val secret = remoteFile("id_rsa")
        val shared = remoteFile("shared.txt")
        Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"))
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"))

        sftp.download(entry("/id_rsa"), localRoot.resolve("id_rsa"), replace = false) {}
        sftp.download(entry("/shared.txt"), localRoot.resolve("shared.txt"), replace = false) {}

        assertEquals("rw-------", mode(localRoot.resolve("id_rsa")))
        assertEquals("rwxr-xr-x", mode(localRoot.resolve("shared.txt")))
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `downloading a link uses the size and mode of the file behind it`() = runBlocking {
        val secret = remoteFile("id_rsa", "x".repeat(5_000))
        Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"))
        Files.createSymbolicLink(remoteRoot.resolve("link"), secret)
        val updates = mutableListOf<TransferProgress>()

        sftp.download(entry("/link"), localRoot.resolve("link"), replace = false) { updates += it }

        assertEquals("rw-------", mode(localRoot.resolve("link")))
        assertEquals(TransferProgress(5_000, 5_000, 1, 1), updates.last())
        assertFalse(Files.isSymbolicLink(localRoot.resolve("link")))
    }

    @Test
    fun `uploads a file and reports progress`() = runBlocking {
        val data = "y".repeat(300_000)
        val source = localRoot.resolve("up.bin").also { Files.writeString(it, data) }
        val updates = mutableListOf<TransferProgress>()

        sftp.upload(source, "/up.bin", replace = false) { updates += it }

        assertEquals(data, Files.readString(remoteRoot.resolve("up.bin")))
        assertEquals(TransferProgress(300_000, 300_000, 1, 1), updates.last())
    }

    @Test
    fun `uploading refuses to overwrite unless told to`() = runBlocking {
        val existing = remoteFile("a.txt", "old")
        val source = localRoot.resolve("a.txt").also { Files.writeString(it, "new") }

        assertFailsWith<SftpFailure> { sftp.upload(source, "/a.txt", replace = false) {} }
        assertEquals("old", Files.readString(existing))

        sftp.upload(source, "/a.txt", replace = true) {}
        assertEquals("new", Files.readString(existing))
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `an uploaded private file stays private, and replacing keeps the existing mode`() = runBlocking {
        val source = localRoot.resolve("id_rsa").also { Files.writeString(it, "key") }
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rw-------"))

        sftp.upload(source, "/id_rsa", replace = false) {}
        assertEquals("rw-------", mode(remoteRoot.resolve("id_rsa")))

        val shared = remoteFile("shared", "old")
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rw-r--r--"))
        sftp.upload(source, "/shared", replace = true) {}
        assertEquals("rw-r--r--", mode(shared))
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `uploads a folder, keeps a private folder private and skips links`() = runBlocking {
        val tree = Files.createDirectories(localRoot.resolve("tree/sub"))
        Files.writeString(tree.resolve("b.txt"), "b")
        Files.writeString(localRoot.resolve("tree/a.txt"), "a")
        Files.setPosixFilePermissions(tree, PosixFilePermissions.fromString("rwx------"))
        val outside = Files.writeString(tempDir.resolve("outside-secret"), "hidden")
        Files.createSymbolicLink(localRoot.resolve("tree/escape"), outside)

        val summary = sftp.upload(localRoot.resolve("tree"), "/tree", replace = false) {}

        assertEquals("a", Files.readString(remoteRoot.resolve("tree/a.txt")))
        assertEquals("b", Files.readString(remoteRoot.resolve("tree/sub/b.txt")))
        assertEquals("rwx------", mode(remoteRoot.resolve("tree/sub")))
        assertFalse(Files.exists(remoteRoot.resolve("tree/escape"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals(listOf("escape: is a link"), summary.skipped)
    }

    @Test
    fun `a cancelled upload of a new file removes the partial file`() = runBlocking {
        val source = localRoot.resolve("big.bin").also { Files.writeString(it, "x".repeat(1_000_000)) }

        assertFailsWith<CancellationException> {
            sftp.upload(source, "/big.bin", replace = false) { progress ->
                if (progress.bytesDone > 0) throw CancellationException("stop")
                Thread.sleep(150)
            }
        }

        assertFalse(Files.exists(remoteRoot.resolve("big.bin")))
    }

    private fun mode(path: Path) = PosixFilePermissions.toString(Files.getPosixFilePermissions(path))
}

/** A real server can't send these names, so this one is scripted. */
class HostileSftpServerTest {

    private fun entry(name: String, directory: Boolean = false): SftpClient.DirEntry {
        val attributes = SftpClient.Attributes().apply {
            type = if (directory) org.apache.sshd.sftp.common.SftpConstants.SSH_FILEXFER_TYPE_DIRECTORY
            else org.apache.sshd.sftp.common.SftpConstants.SSH_FILEXFER_TYPE_REGULAR
        }
        return SftpClient.DirEntry(name, name, attributes)
    }

    private fun clientListing(vararg entries: SftpClient.DirEntry): SftpClient {
        val handle = mockk<SftpClient.CloseableHandle>(relaxed = true)
        return mockk(relaxed = true) {
            every { openDir(any()) } returns handle
            every { readDir(handle) } returnsMany listOf(entries.toList(), null)
        }
    }

    @Test
    fun `names that climb out of the folder never reach a listing`() = runBlocking {
        val client = clientListing(
            entry(".", directory = true),
            entry("..", directory = true),
            entry("../../etc/passwd"),
            entry("/etc/passwd"),
            entry("fine.txt"),
        )

        val listed = MinaSftpSession(client).list("/home/alice").files.map(RemoteFile::path)

        assertEquals(listOf("/home/alice/fine.txt"), listed)
    }

    @Test
    fun `deleting a folder never deletes through a dot-dot entry`() = runBlocking {
        val client = clientListing(entry("..", directory = true), entry("../sibling"), entry("fine.txt"))
        val folder = RemoteFile("target", "/home/alice/target", RemoteFileKind.Directory)

        MinaSftpSession(client).delete(folder)

        verify(exactly = 1) { client.remove("/home/alice/target/fine.txt") }
        verify(exactly = 1) { client.rmdir("/home/alice/target") }
        verify(exactly = 0) { client.rmdir("/home/alice/target/..") }
        verify(exactly = 0) { client.remove("/home/alice/target/../sibling") }
    }

    @Test
    fun `a download never writes outside the chosen folder`() = runBlocking {
        val target = Files.createTempDirectory("sshellspace-hostile")
        try {
            val client = clientListing(entry("..\\..\\evil"), entry("C:evil"), entry("CON"))
            val folder = RemoteFile("tree", "/tree", RemoteFileKind.Directory)

            val summary = MinaSftpSession(client).download(folder, target.resolve("tree"), replace = false) {}

            assertEquals(3, summary.skipped.size)
            verify(exactly = 0) { client.read(any<String>()) }
        } finally {
            target.toFile().deleteRecursively()
        }
    }
}
