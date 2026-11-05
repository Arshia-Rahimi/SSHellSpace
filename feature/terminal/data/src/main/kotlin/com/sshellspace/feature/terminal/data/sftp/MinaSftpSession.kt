package com.sshellspace.feature.terminal.data.sftp

import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.RemoteListing
import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.SftpSession
import com.sshellspace.feature.terminal.domain.TransferProgress
import com.sshellspace.feature.terminal.domain.TransferSummary
import com.sshellspace.feature.terminal.domain.isSafeLocalName
import com.sshellspace.feature.terminal.domain.isSafeRemoteName
import com.sshellspace.feature.terminal.domain.joinRemotePath
import com.sshellspace.feature.terminal.domain.printableRemoteText
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClient.OpenMode
import org.apache.sshd.sftp.common.SftpConstants
import org.apache.sshd.sftp.common.SftpException

internal class MinaSftpSession(private val client: SftpClient) : SftpSession {

    override suspend fun home(): String = sftp(".") { client.canonicalPath(".") }

    override suspend fun resolve(path: String): RemoteFile = sftp(path) {
        val canonical = client.canonicalPath(path)
        val attributes = client.stat(canonical)
        RemoteFile(
            name = canonical.substringAfterLast('/'),
            path = canonical,
            kind = attributes.kind(),
            size = attributes.size,
            modifiedMillis = attributes.modifyTime?.toMillis(),
            permissions = attributes.mode(),
        )
    }

    override suspend fun list(path: String): RemoteListing = sftp(path) { readDirectory(path) }

    override suspend fun readBytes(file: RemoteFile, maxBytes: Long): ByteArray = sftp(file.path) {
        val attributes = client.stat(file.path)
        if (!attributes.isRegularFile) throw SftpFailure("${file.displayName} isn't a regular file.")
        if (attributes.size > maxBytes) throw tooLargeToRead(file.displayName)
        client.read(file.path).use { it.readLimited(maxBytes, file.displayName) }
    }

    override suspend fun exists(path: String): Boolean = sftp(path) {
        try {
            client.lstat(path)
            true
        } catch (e: SftpException) {
            if (e.status != SftpConstants.SSH_FX_NO_SUCH_FILE) throw e
            false
        }
    }

    override suspend fun createDirectory(path: String) = sftp(path) { client.mkdir(path) }

    override suspend fun rename(from: String, to: String) = sftp(from) { client.rename(from, to) }

    override suspend fun delete(file: RemoteFile) = sftp(file.path) {
        if (file.isDirectory && !file.isSymlink) deleteContents(file.path, depth = 0)
        if (file.isDirectory && !file.isSymlink) client.rmdir(file.path) else client.remove(file.path)
    }

    private suspend fun deleteContents(directory: String, depth: Int) {
        if (depth >= MAX_DEPTH) throw SftpFailure("${printableRemoteText(directory)} is nested too deeply to delete.")
        do {
            val listing = readDirectory(directory)
            listing.files.forEach { entry ->
                currentCoroutineContext().ensureActive()
                if (entry.isDirectory && !entry.isSymlink) {
                    deleteContents(entry.path, depth + 1)
                    client.rmdir(entry.path)
                } else {
                    client.remove(entry.path)
                }
            }
        } while (listing.truncated)
    }

    override suspend fun download(
        file: RemoteFile,
        target: Path,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary = sftp(file.path) {
        val source = if (file.isSymlink) file.withLinkTargetAttributes() else file
        val plan = DownloadPlan()
        when (source.kind) {
            RemoteFileKind.Directory -> collectDownloads(source.path, target, depth = 0, plan)
            RemoteFileKind.File -> plan.add(source, target)
            RemoteFileKind.Other -> throw SftpFailure("${source.displayName} isn't a regular file, so it can't be downloaded.")
        }
        if (!replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw SftpFailure("${target.fileName} already exists in that folder.")
        }

        val progress = ProgressReporter(plan.bytes, plan.files.size, onProgress)
        if (source.isDirectory) createLocalDirectory(target, source.permissions)
        plan.directories.forEach { (directory, mode) -> createLocalDirectory(directory, mode) }
        plan.files.forEach { (remote, local) -> downloadFile(remote, local, replace, progress) }
        progress.finish()
        TransferSummary(plan.skipped)
    }

    /** A listing gives a link's own size and mode, but a private file behind a link has to stay private. */
    private fun RemoteFile.withLinkTargetAttributes(): RemoteFile {
        val attributes = client.stat(path)
        return copy(kind = attributes.kind(), size = attributes.size, permissions = attributes.mode())
    }

    private suspend fun collectDownloads(directory: String, target: Path, depth: Int, plan: DownloadPlan) {
        if (depth >= MAX_DEPTH) {
            plan.skipped += "${printableRemoteText(directory)}: nested too deeply"
            return
        }
        readDirectory(directory).files.forEach { entry ->
            currentCoroutineContext().ensureActive()
            val local = target.resolve(entry.name)
            when {
                entry.isSymlink -> plan.skipped += "${entry.displayName}: is a link"
                !isSafeLocalName(entry.name) || !local.normalize().startsWith(target.normalize()) ->
                    plan.skipped += "${entry.displayName}: name isn't safe on this computer"
                entry.isDirectory -> {
                    plan.directories += local to entry.permissions
                    collectDownloads(entry.path, local, depth + 1, plan)
                }
                entry.kind == RemoteFileKind.File -> plan.add(entry, local)
                else -> plan.skipped += "${entry.displayName}: isn't a regular file"
            }
        }
    }

    private suspend fun downloadFile(remote: RemoteFile, target: Path, replace: Boolean, progress: ProgressReporter) {
        // Created private, so nothing else can read it while it arrives, and doesn't look like the finished file.
        val part = Files.createTempFile(target.parent, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            client.read(remote.path).use { input ->
                Files.newOutputStream(part).use { output -> input.copyWithProgress(output, progress) }
            }
            applyLocalMode(part, (remote.permissions ?: DEFAULT_FILE_MODE) and DOWNLOAD_MODE_MASK)
            val options = if (replace) arrayOf<CopyOption>(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
            Files.move(part, target, *options)
            progress.fileDone()
        } finally {
            runCatching { Files.deleteIfExists(part) }
        }
    }

    override suspend fun upload(
        source: Path,
        targetPath: String,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary = sftp(targetPath) {
        val plan = UploadPlan()
        if (Files.isDirectory(source)) collectUploads(source, targetPath, plan) else plan.add(source, targetPath)

        val progress = ProgressReporter(plan.bytes, plan.files.size, onProgress)
        plan.directories.forEachIndexed { index, (local, remote) ->
            createRemoteDirectory(local, remote, mustBeNew = index == 0 && !replace)
        }
        plan.files.forEach { (local, remote) -> uploadFile(local, remote, replace, progress) }
        progress.finish()
        TransferSummary(plan.skipped)
    }

    private fun collectUploads(source: Path, targetPath: String, plan: UploadPlan) {
        // Doesn't follow links, so a link in the folder can't send what it points to.
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    plan.directories += dir to remotePathOf(source, dir, targetPath)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) {
                        plan.add(file, remotePathOf(source, file, targetPath))
                    } else {
                        val kind = if (attrs.isSymbolicLink) "is a link" else "isn't a regular file"
                        plan.skipped += "${source.relativize(file)}: $kind"
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun remotePathOf(root: Path, item: Path, targetPath: String): String =
        root.relativize(item).filter { it.toString().isNotEmpty() }
            .fold(targetPath) { path, part -> joinRemotePath(path, part.toString()) }

    private fun createRemoteDirectory(local: Path, remote: String, mustBeNew: Boolean) {
        if (!mustBeNew && remoteExists(remote)) return
        client.mkdir(remote)
        // A folder the user kept private shouldn't open up by being uploaded.
        localMode(local)?.let { mode -> runCatching { client.setStat(remote, SftpClient.Attributes().perms(mode)) } }
    }

    private suspend fun uploadFile(local: Path, remote: String, replace: Boolean, progress: ProgressReporter) {
        val overwrites = replace && remoteExists(remote)
        val modes = if (overwrites) {
            listOf(OpenMode.Write, OpenMode.Truncate)
        } else {
            // Set before there's any data: the server's default would make a private key readable by everyone.
            client.open(remote, OpenMode.Write, OpenMode.Create, OpenMode.Exclusive).use { handle ->
                localMode(local)?.let { mode ->
                    runCatching { client.setStat(handle, SftpClient.Attributes().perms(mode)) }
                }
            }
            listOf(OpenMode.Write)
        }
        try {
            Files.newInputStream(local).use { input ->
                client.write(remote, modes).use { output -> input.copyWithProgress(output, progress) }
            }
            progress.fileDone()
        } catch (e: Exception) {
            // Half a new file would look like a whole one. Half a replaced one has nothing better to go back to.
            if (!overwrites) runCatching { client.remove(remote) }
            throw e
        }
    }

    private fun remoteExists(path: String): Boolean = try {
        client.lstat(path)
        true
    } catch (e: SftpException) {
        if (e.status != SftpConstants.SSH_FX_NO_SUCH_FILE) throw e
        false
    }

    private fun readDirectory(path: String): RemoteListing {
        val files = mutableListOf<RemoteFile>()
        var truncated = false
        var linkLookups = 0
        client.openDir(path).use { handle ->
            while (!truncated) {
                val batch = client.readDir(handle)?.takeIf { it.isNotEmpty() } ?: break
                for (entry in batch) {
                    if (!isSafeRemoteName(entry.filename)) continue
                    if (files.size == MAX_LISTING_SIZE) {
                        truncated = true
                        break
                    }
                    val attributes = entry.attributes
                    val entryPath = joinRemotePath(path, entry.filename)
                    val kind = when {
                        !attributes.isSymbolicLink -> attributes.kind()
                        linkLookups++ < MAX_LINK_LOOKUPS -> linkTargetKind(entryPath)
                        else -> RemoteFileKind.Other
                    }
                    files += RemoteFile(
                        name = entry.filename,
                        path = entryPath,
                        kind = kind,
                        isSymlink = attributes.isSymbolicLink,
                        size = attributes.size,
                        modifiedMillis = attributes.modifyTime?.toMillis(),
                        permissions = attributes.mode(),
                    )
                }
            }
        }
        return RemoteListing(files, truncated)
    }

    private fun linkTargetKind(path: String): RemoteFileKind = try {
        client.stat(path).kind()
    } catch (_: IOException) {
        RemoteFileKind.Other
    }

    private fun SftpClient.Attributes.kind(): RemoteFileKind = when {
        isDirectory -> RemoteFileKind.Directory
        isRegularFile -> RemoteFileKind.File
        else -> RemoteFileKind.Other
    }

    private fun SftpClient.Attributes.mode(): Int? = (permissions and MODE_MASK).takeIf { it != 0 }

    private suspend fun <T> sftp(path: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: SftpException) {
            throw SftpFailure(describe(e, path))
        } catch (e: AccessDeniedException) {
            throw SftpFailure("This computer won't allow using ${e.file}.")
        } catch (e: FileAlreadyExistsException) {
            throw SftpFailure("${e.file} already exists.")
        } catch (e: FileSystemException) {
            throw SftpFailure("Couldn't use ${e.file}: ${e.reason ?: e.message}")
        } catch (e: IOException) {
            throw SftpFailure("Lost contact with the server: ${e.message}")
        }
    }

    private fun describe(e: SftpException, path: String): String {
        val name = printableRemoteText(path)
        return when (e.status) {
            SftpConstants.SSH_FX_NO_SUCH_FILE -> "$name doesn't exist."
            SftpConstants.SSH_FX_PERMISSION_DENIED -> "You don't have permission to do that with $name."
            SftpConstants.SSH_FX_FILE_ALREADY_EXISTS -> "$name already exists."
            SftpConstants.SSH_FX_DIR_NOT_EMPTY -> "$name still has files in it."
            SftpConstants.SSH_FX_NO_SPACE_ON_FILESYSTEM, SftpConstants.SSH_FX_QUOTA_EXCEEDED ->
                "The server is out of space for $name."
            // OpenSSH reports most refusals this way, such as a name that's taken, without saying why.
            SftpConstants.SSH_FX_FAILURE -> "The server refused to do that with $name. The name may be taken."
            else -> "The server couldn't do that with $name: ${printableRemoteText(e.message.orEmpty())}"
        }
    }

    override fun close() {
        runCatching { client.close() }
    }

    private class DownloadPlan {
        val directories = mutableListOf<Pair<Path, Int?>>()
        val files = mutableListOf<Pair<RemoteFile, Path>>()
        val skipped = mutableListOf<String>()
        var bytes = 0L
            private set

        fun add(file: RemoteFile, target: Path) {
            if (files.size == MAX_TREE_FILES) throw SftpFailure("That folder holds over $MAX_TREE_FILES files.")
            files += file to target
            bytes += file.size
        }
    }

    private class UploadPlan {
        val directories = mutableListOf<Pair<Path, String>>()
        val files = mutableListOf<Pair<Path, String>>()
        val skipped = mutableListOf<String>()
        var bytes = 0L
            private set

        fun add(file: Path, target: String) {
            files += file to target
            bytes += Files.size(file)
        }
    }

    private companion object {
        /** Past this a hostile server could make a listing use up this app's memory. */
        const val MAX_LISTING_SIZE = 100_000
        const val MAX_LINK_LOOKUPS = 1_000

        /** Stops a server that invents folders forever. Real trees run out of path length well before this. */
        const val MAX_DEPTH = 64

        // Written as an octal string since Kotlin has no octal literals.
        val MODE_MASK = "777".toInt(radix = 8)
    }
}
