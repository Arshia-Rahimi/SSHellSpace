package com.sshellspace.feature.terminal.data.sftp

import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.TransferProgress
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val MAX_TREE_FILES = 500_000
internal const val TEMP_PREFIX = ".sshellspace-"
internal const val TEMP_SUFFIX = ".part"
private const val BUFFER_SIZE = 64 * 1024
private const val REPORT_INTERVAL_NANOS = 100_000_000L
private const val LAST_MODE_BIT = 8

// Written as octal strings since Kotlin has no octal literals.
private val OWNER_ALL = "700".toInt(radix = 8)
internal val DEFAULT_FILE_MODE = "644".toInt(radix = 8)
private val DEFAULT_DIRECTORY_MODE = "755".toInt(radix = 8)

/** Like a umask of 022: nothing that's copied here has any business becoming writable by others. */
internal val DOWNLOAD_MODE_MASK = "755".toInt(radix = 8)

internal suspend fun InputStream.copyWithProgress(output: OutputStream, progress: ProgressReporter) {
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) return
        output.write(buffer, 0, read)
        progress.add(read)
    }
}

internal fun tooLargeToRead(displayName: String) = SftpFailure("$displayName is too large to open here.")

/** Stops as soon as there's too much, since a size from a listing can't be trusted to say how much will come. */
internal suspend fun InputStream.readLimited(maxBytes: Long, displayName: String): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) return output.toByteArray()
        output.write(buffer, 0, read)
        if (output.size() > maxBytes) throw tooLargeToRead(displayName)
    }
}

internal fun createLocalDirectory(directory: Path, sourceMode: Int?) {
    val isNew = !Files.isDirectory(directory)
    Files.createDirectories(directory)
    // The owner needs to get in to write the contents. Others get at most what the source gave them to read.
    if (isNew) applyLocalMode(directory, ((sourceMode ?: DEFAULT_DIRECTORY_MODE) and DOWNLOAD_MODE_MASK) or OWNER_ALL)
}

internal fun applyLocalMode(path: Path, mode: Int) {
    val permissions = PosixFilePermission.entries.filterIndexedTo(mutableSetOf()) { index, _ ->
        mode and (1 shl (LAST_MODE_BIT - index)) != 0
    }
    try {
        Files.setPosixFilePermissions(path, permissions)
    } catch (_: UnsupportedOperationException) {
        // Windows has no POSIX modes.
    }
}

internal fun localMode(path: Path, vararg options: LinkOption): Int? = try {
    Files.getPosixFilePermissions(path, *options).fold(0) { mode, permission ->
        mode or (1 shl (LAST_MODE_BIT - permission.ordinal))
    }
} catch (_: UnsupportedOperationException) {
    null
}

/** Called for every chunk, so it only passes on a few updates a second. */
internal class ProgressReporter(
    private val bytesTotal: Long,
    private val filesTotal: Int,
    private val onProgress: (TransferProgress) -> Unit,
) {
    private var bytesDone = 0L
    private var filesDone = 0
    private var lastReport = 0L

    init {
        report()
    }

    fun add(bytes: Int) {
        bytesDone += bytes
        val now = System.nanoTime()
        if (now - lastReport >= REPORT_INTERVAL_NANOS) report(now)
    }

    fun fileDone() {
        filesDone++
    }

    fun finish() = report()

    private fun report(now: Long = System.nanoTime()) {
        lastReport = now
        onProgress(TransferProgress(bytesDone, bytesTotal, filesDone, filesTotal))
    }
}
