package com.sshellspace.feature.terminal.data.sftp

import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.SftpFailure
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

class LocalFileSessionTest {

    @TempDir
    lateinit var root: Path

    private val session by lazy { LocalFileSession(homeDirectory = root.toString()) }

    private fun write(relative: String, text: String = "data"): Path =
        root.resolve(relative).also {
            Files.createDirectories(it.parent)
            Files.writeString(it, text)
        }

    private fun Path.asRemote(): RemoteFile = runBlocking { session.resolve(this@asRemote.toString()) }

    @Test
    fun `lists a folder, telling apart files, folders and links`() = runBlocking {
        val file = write("notes.txt", "hello")
        Files.createDirectory(root.resolve("sub"))
        Files.createSymbolicLink(root.resolve("to-sub"), root.resolve("sub"))
        Files.createSymbolicLink(root.resolve("to-file"), file)
        Files.createSymbolicLink(root.resolve("broken"), root.resolve("nothing"))

        val listed = session.list(root.toString()).files.associateBy { it.name }

        assertEquals(root.toRealPath().toString(), session.home())
        assertEquals(RemoteFileKind.File, listed.getValue("notes.txt").kind)
        assertEquals(5L, listed.getValue("notes.txt").size)
        assertEquals(RemoteFileKind.Directory, listed.getValue("sub").kind)
        assertEquals(RemoteFileKind.Directory, listed.getValue("to-sub").kind)
        assertTrue(listed.getValue("to-sub").isSymlink)
        assertEquals(5L, listed.getValue("to-file").size)
        assertEquals(RemoteFileKind.Other, listed.getValue("broken").kind)
    }

    @Test
    fun `a folder that isn't there fails with a message fit to show`() = runBlocking<Unit> {
        val failure = assertFailsWith<SftpFailure> { session.list(root.resolve("gone").toString()) }
        assertTrue("doesn't exist" in failure.message, failure.message)
    }

    @Test
    fun `renaming refuses a name that is taken`() = runBlocking<Unit> {
        val from = write("a.txt", "keep me")
        val taken = write("b.txt", "and me")

        assertFailsWith<SftpFailure> { session.rename(from.toString(), taken.toString()) }

        assertEquals("keep me", Files.readString(from))
        assertEquals("and me", Files.readString(taken))
    }

    @Test
    fun `deleting a folder leaves what a link inside it points to`() = runBlocking {
        val outside = write("outside/precious.txt")
        write("doomed/inner/file.txt")
        Files.createSymbolicLink(root.resolve("doomed/link"), root.resolve("outside"))

        session.delete(root.resolve("doomed").asRemote())

        assertFalse(Files.exists(root.resolve("doomed")))
        assertTrue(Files.exists(outside))
    }

    @Test
    fun `deleting a link removes the link, not the folder behind it`() = runBlocking {
        val kept = write("real/file.txt")
        val link = Files.createSymbolicLink(root.resolve("link"), root.resolve("real"))
        val listed = session.list(root.toString()).files.single { it.name == "link" }

        session.delete(listed)

        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(kept))
    }

    @Test
    fun `copying a folder skips links inside it and reports them`() = runBlocking {
        write("src/a.txt", "a")
        write("src/deep/b.txt", "b")
        write("secret.txt", "secret")
        Files.createSymbolicLink(root.resolve("src/link.txt"), root.resolve("secret.txt"))
        val updates = mutableListOf<Long>()

        val summary = session.download(root.resolve("src").asRemote(), root.resolve("dest"), false) { updates += it.bytesDone }

        assertEquals("a", Files.readString(root.resolve("dest/a.txt")))
        assertEquals("b", Files.readString(root.resolve("dest/deep/b.txt")))
        assertFalse(Files.exists(root.resolve("dest/link.txt"), LinkOption.NOFOLLOW_LINKS))
        assertEquals(listOf("link.txt: is a link"), summary.skipped)
        assertEquals(2L, updates.last())
    }

    @Test
    fun `a copy into a folder of its own is refused`() = runBlocking<Unit> {
        write("src/a.txt")

        val failure = assertFailsWith<SftpFailure> {
            session.download(root.resolve("src").asRemote(), root.resolve("src/inside"), false) {}
        }

        assertTrue("into itself" in failure.message, failure.message)
        assertFalse(Files.exists(root.resolve("src/inside")))
    }

    @Test
    fun `replacing a file with itself is refused and leaves it whole`() = runBlocking<Unit> {
        val file = write("a.txt", "contents")

        assertFailsWith<SftpFailure> { session.upload(file, file.toString(), true) {} }

        assertEquals("contents", Files.readString(file))
    }

    @Test
    fun `a copy doesn't overwrite unless told to`() = runBlocking {
        val source = write("a.txt", "new")
        val target = write("dest/a.txt", "old")

        assertFailsWith<SftpFailure> { session.upload(source, target.toString(), false) {} }
        assertEquals("old", Files.readString(target))

        session.upload(source, target.toString(), true) {}
        assertEquals("new", Files.readString(target))
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `a private file stays private when copied`() = runBlocking {
        val secret = write("id_ed25519", "key")
        Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"))

        session.upload(secret, root.resolve("copy").toString(), false) {}

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve("copy"))))
    }

    @Test
    fun `reading stops at the limit and only reads regular files`() = runBlocking<Unit> {
        val small = write("small.txt", "12345")
        assertContentEquals("12345".toByteArray(), session.readBytes(small.asRemote(), 5))

        val failure = assertFailsWith<SftpFailure> { session.readBytes(small.asRemote(), 4) }
        assertTrue("too large" in failure.message, failure.message)

        val folder = Files.createDirectory(root.resolve("folder"))
        assertFailsWith<SftpFailure> { session.readBytes(folder.asRemote(), 100) }
    }

    @Test
    fun `reading goes through a link the user picked`() = runBlocking {
        val file = write("real.txt", "behind a link")
        Files.createSymbolicLink(root.resolve("link.txt"), file)
        val link = session.list(root.toString()).files.single { it.name == "link.txt" }

        assertContentEquals("behind a link".toByteArray(), session.readBytes(link, 100))
    }

    @Test
    fun `windows paths become the browser's and back`() {
        val windows = LocalPaths(separator = '\\')

        assertEquals("/C:/Users/alice", windows.toBrowser("C:\\Users\\alice"))
        assertEquals("/C:", windows.toBrowser("C:\\"))
        assertEquals("C:\\Users\\alice", windows.toNative("/C:/Users/alice"))
        assertEquals("C:\\", windows.toNative("/C:"))
        assertEquals("C:\\Users", windows.toNative("C:\\Users"))
        assertEquals("\\\\server\\share", windows.toNative("//server/share"))
        assertTrue(windows.isDriveList("/"))
        assertTrue(windows.isAbsolute("D:\\logs"))
        assertFalse(windows.isAbsolute("logs"))
    }

    @Test
    fun `paths on other systems are left alone`() {
        val posix = LocalPaths(separator = '/')

        assertEquals("/home/alice", posix.toBrowser("/home/alice"))
        assertEquals("/home/alice", posix.toNative("/home/alice"))
        assertFalse(posix.isDriveList("/"))
        assertFalse(posix.isAbsolute("C:\\logs"))
    }
}
