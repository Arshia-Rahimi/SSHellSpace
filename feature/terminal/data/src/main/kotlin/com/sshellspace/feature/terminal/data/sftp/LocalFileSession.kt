package com.sshellspace.feature.terminal.data.sftp

import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.RemoteListing
import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.SftpSession
import com.sshellspace.feature.terminal.domain.TransferProgress
import com.sshellspace.feature.terminal.domain.TransferSummary
import com.sshellspace.feature.terminal.domain.isSafeRemoteName
import com.sshellspace.feature.terminal.domain.printableRemoteText
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val MAX_LISTING_SIZE = 100_000
private const val ROOT = "/"
private val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:([\\\\/].*)?$")

/**
 * The browser's paths are absolute and `/`-separated whatever the computer, the way a server's are. On Windows that
 * makes `C:\Users` into `/C:/Users`, and [ROOT] the list of drives.
 */
internal class LocalPaths(private val separator: Char = File.separatorChar) {
    private val isWindows get() = separator == '\\'

    fun isDriveList(path: String) = isWindows && path == ROOT

    fun isAbsolute(text: String) = text.startsWith(ROOT) || (isWindows && (WINDOWS_DRIVE_PATH.matches(text) || text.startsWith("\\\\")))

    fun toBrowser(native: String): String {
        if (!isWindows) return native
        val slashes = native.replace('\\', '/').trimEnd('/')
        return if (slashes.startsWith(ROOT)) slashes else "$ROOT$slashes"
    }

    fun toNative(browser: String): String {
        if (!isWindows) return browser
        // Also takes `C:\Users` as typed. A network path's `//server` keeps both of its slashes.
        val path = if (browser.startsWith("//")) browser else browser.removePrefix(ROOT)
        val backslashes = path.replace('/', '\\')
        return if (backslashes.endsWith(':')) "$backslashes\\" else backslashes
    }
}

/**
 * This computer's own files, for browsing with no SSH involved. Folders are walked without following links, as they are
 * for a server: a link inside a folder could reach outside what the user chose, and deleting through one would remove
 * what it points to.
 */
internal class LocalFileSession(
    private val homeDirectory: String = System.getProperty("user.home"),
    private val paths: LocalPaths = LocalPaths(),
) : SftpSession {

    /** Its real path, as [resolve] gives, or one folder would have two paths when a link leads to it. */
    override suspend fun home(): String = local(homeDirectory) { paths.toBrowser(Path.of(homeDirectory).toRealPath().toString()) }

    override fun isAbsolutePath(text: String) = paths.isAbsolute(text)

    override suspend fun resolve(path: String): RemoteFile = local(path) {
        if (paths.isDriveList(path)) return@local RemoteFile("", ROOT, RemoteFileKind.Directory)
        val real = nativePath(path).toRealPath()
        val attributes = Files.readAttributes(real, BasicFileAttributes::class.java)
        RemoteFile(
            name = real.fileName?.toString().orEmpty(),
            path = paths.toBrowser(real.toString()),
            kind = attributes.kind(),
            size = attributes.size(),
            modifiedMillis = attributes.lastModifiedTime().toMillis(),
            permissions = localMode(real),
        )
    }

    override suspend fun list(path: String): RemoteListing = local(path) {
        if (paths.isDriveList(path)) {
            return@local RemoteListing(
                File.listRoots().map {
                    val browserPath = paths.toBrowser(it.path)
                    RemoteFile(browserPath.removePrefix(ROOT), browserPath, RemoteFileKind.Directory)
                },
            )
        }
        val files = mutableListOf<RemoteFile>()
        var truncated = false
        Files.newDirectoryStream(nativePath(path)).use { stream ->
            for (entry in stream) {
                if (files.size == MAX_LISTING_SIZE) {
                    truncated = true
                    break
                }
                describe(entry)?.let { files += it }
            }
        }
        RemoteListing(files, truncated)
    }

    /** Null for an entry that went away while listing, or that this user can't even look at. */
    private fun describe(entry: Path): RemoteFile? {
        val name = entry.fileName?.toString().orEmpty()
        if (!isSafeRemoteName(name)) return null
        return try {
            val attributes = Files.readAttributes(entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val kind = when {
                !attributes.isSymbolicLink -> attributes.kind()
                Files.isDirectory(entry) -> RemoteFileKind.Directory
                Files.isRegularFile(entry) -> RemoteFileKind.File
                else -> RemoteFileKind.Other
            }
            RemoteFile(
                name = name,
                path = paths.toBrowser(entry.toString()),
                kind = kind,
                isSymlink = attributes.isSymbolicLink,
                size = if (attributes.isSymbolicLink && kind == RemoteFileKind.File) Files.size(entry) else attributes.size(),
                modifiedMillis = attributes.lastModifiedTime().toMillis(),
                permissions = localMode(entry, LinkOption.NOFOLLOW_LINKS),
            )
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun exists(path: String): Boolean = local(path) {
        paths.isDriveList(path) || Files.exists(nativePath(path), LinkOption.NOFOLLOW_LINKS)
    }

    override suspend fun readBytes(file: RemoteFile, maxBytes: Long): ByteArray = local(file.path) {
        val path = nativePath(file.path)
        if (!Files.isRegularFile(path)) throw SftpFailure("${file.displayName} isn't a regular file.")
        if (Files.size(path) > maxBytes) throw tooLargeToRead(file.displayName)
        Files.newInputStream(path).use { it.readLimited(maxBytes, file.displayName) }
    }

    override suspend fun createDirectory(path: String) = local(path) {
        Files.createDirectory(nativePath(path))
        Unit
    }

    /** Without replacing, so a name that's taken fails rather than losing what has it. */
    override suspend fun rename(from: String, to: String) = local(from) {
        Files.move(nativePath(from), nativePath(to))
        Unit
    }

    override suspend fun delete(file: RemoteFile) = local(file.path) {
        val path = nativePath(file.path)
        if (file.isDirectory && !file.isSymlink) deleteTree(path, currentCoroutineContext()) else Files.delete(path)
    }

    private fun deleteTree(root: Path, context: CoroutineContext) {
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    override suspend fun download(
        file: RemoteFile,
        target: Path,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary = local(file.path) { copy(nativePath(file.path), target, replace, onProgress) }

    override suspend fun upload(
        source: Path,
        targetPath: String,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary = local(targetPath) { copy(source, nativePath(targetPath), replace, onProgress) }

    private suspend fun copy(
        picked: Path,
        target: Path,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary {
        // A link the user picked directly is followed.
        val source = picked.toRealPath()
        val name = printableRemoteText(picked.fileName?.toString() ?: picked.toString())
        // A folder into itself would never end, and a file onto itself would be gone once its copy was half written.
        val realTarget = (target.parent?.toRealPath() ?: target).resolve(target.fileName ?: target)
        if (realTarget.startsWith(source)) throw SftpFailure("$name can't be copied into itself.")
        if (!replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw SftpFailure("$name already exists in that folder.")

        val plan = CopyPlan()
        when {
            Files.isDirectory(source) -> collect(source, target, plan, currentCoroutineContext())
            Files.isRegularFile(source) -> plan.add(source, target)
            else -> throw SftpFailure("$name isn't a regular file, so it can't be copied.")
        }
        val progress = ProgressReporter(plan.bytes, plan.files.size, onProgress)
        plan.directories.forEach { (from, to) -> createLocalDirectory(to, localMode(from)) }
        plan.files.forEach { (from, to) -> copyFile(from, to, replace, progress) }
        progress.finish()
        return TransferSummary(plan.skipped)
    }

    private fun collect(source: Path, target: Path, plan: CopyPlan, context: CoroutineContext) {
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    plan.directories += dir to target.resolve(source.relativize(dir).toString())
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relative = source.relativize(file)
                    when {
                        attrs.isRegularFile -> plan.add(file, target.resolve(relative.toString()))
                        attrs.isSymbolicLink -> plan.skipped += "${printableRemoteText(relative.toString())}: is a link"
                        else -> plan.skipped += "${printableRemoteText(relative.toString())}: isn't a regular file"
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    plan.skipped += "${printableRemoteText(source.relativize(file).toString())}: can't be read"
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private suspend fun copyFile(source: Path, target: Path, replace: Boolean, progress: ProgressReporter) {
        // Created private, so nothing else can read it meanwhile, and doesn't look like the finished file.
        val part = Files.createTempFile(target.parent, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            Files.newInputStream(source).use { input ->
                Files.newOutputStream(part).use { output -> input.copyWithProgress(output, progress) }
            }
            applyLocalMode(part, (localMode(source) ?: DEFAULT_FILE_MODE) and DOWNLOAD_MODE_MASK)
            val options = if (replace) arrayOf<CopyOption>(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
            Files.move(part, target, *options)
            progress.fileDone()
        } finally {
            runCatching { Files.deleteIfExists(part) }
        }
    }

    private fun nativePath(path: String): Path = Path.of(paths.toNative(path))

    private fun BasicFileAttributes.kind(): RemoteFileKind = when {
        isDirectory -> RemoteFileKind.Directory
        isRegularFile -> RemoteFileKind.File
        else -> RemoteFileKind.Other
    }

    private suspend fun <T> local(path: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        val name = printableRemoteText(path)
        try {
            block()
        } catch (_: NoSuchFileException) {
            throw SftpFailure("$name doesn't exist.")
        } catch (_: AccessDeniedException) {
            throw SftpFailure("This computer won't allow using $name.")
        } catch (e: FileAlreadyExistsException) {
            throw SftpFailure("${printableRemoteText(e.file ?: path)} already exists.")
        } catch (_: DirectoryNotEmptyException) {
            throw SftpFailure("$name still has files in it.")
        } catch (e: FileSystemException) {
            throw SftpFailure("Couldn't use $name: ${printableRemoteText(e.reason ?: e.message.orEmpty())}")
        } catch (e: IOException) {
            throw SftpFailure("Couldn't use $name: ${printableRemoteText(e.message.orEmpty())}")
        } catch (_: InvalidPathException) {
            throw SftpFailure("$name isn't a path on this computer.")
        }
    }

    override fun close() = Unit

    private class CopyPlan {
        val directories = mutableListOf<Pair<Path, Path>>()
        val files = mutableListOf<Pair<Path, Path>>()
        val skipped = mutableListOf<String>()
        var bytes = 0L
            private set

        fun add(file: Path, target: Path) {
            if (files.size == MAX_TREE_FILES) throw SftpFailure("That folder holds over $MAX_TREE_FILES files.")
            files += file to target
            bytes += Files.size(file)
        }
    }
}
