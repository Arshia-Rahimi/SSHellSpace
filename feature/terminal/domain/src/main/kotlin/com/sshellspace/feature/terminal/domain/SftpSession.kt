package com.sshellspace.feature.terminal.domain

import java.nio.file.Path

/**
 * A file browsing channel on an open SSH session. Everything a server sends is untrusted: names that could escape a
 * folder never appear in a listing, so walking or joining them can't reach anything outside it.
 *
 * Every call throws [SftpFailure], with a message fit to show the user, when the server or the local disk refuses.
 */
interface SftpSession : AutoCloseable {
    /** The folder the server starts in, normally the user's home. */
    suspend fun home(): String

    /** Follows links and relative paths to what [path] really is. */
    suspend fun resolve(path: String): RemoteFile

    suspend fun list(path: String): RemoteListing
    suspend fun exists(path: String): Boolean
    suspend fun createDirectory(path: String)
    suspend fun rename(from: String, to: String)

    /** Deletes [file], and a folder's contents with it. A link is removed itself; what it points to is left alone. */
    suspend fun delete(file: RemoteFile)

    /**
     * Copies [file] to [target], walking a folder's contents. Links inside a folder and files with names unsafe on
     * this computer are skipped and reported. A file only appears at its real name once it arrived whole.
     *
     * @param replace overwrite what's already there, merging into an existing folder. Otherwise that fails.
     */
    suspend fun download(
        file: RemoteFile,
        target: Path,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary

    /**
     * Copies [source] to [targetPath], walking a folder's contents. Links inside a folder are skipped and reported,
     * since following one could send files from outside the folder the user chose. New files keep their local mode.
     *
     * @param replace overwrite what's already there, merging into an existing folder. Otherwise that fails.
     */
    suspend fun upload(
        source: Path,
        targetPath: String,
        replace: Boolean,
        onProgress: (TransferProgress) -> Unit,
    ): TransferSummary
}

class SftpFailure(override val message: String) : Exception(message)

enum class RemoteFileKind { File, Directory, Other }

data class RemoteFile(
    val name: String,
    val path: String,
    /** What the name leads to, following a link. A link with nothing behind it is [RemoteFileKind.Other]. */
    val kind: RemoteFileKind,
    val isSymlink: Boolean = false,
    val size: Long = 0,
    val modifiedMillis: Long? = null,
    /** The POSIX mode bits, when the server reports them. */
    val permissions: Int? = null,
) {
    val isDirectory: Boolean get() = kind == RemoteFileKind.Directory
    val displayName: String get() = printableRemoteText(name)
}

data class RemoteListing(
    val files: List<RemoteFile>,
    /** The folder holds more than a listing keeps, or the server is trying to exhaust this app's memory. */
    val truncated: Boolean = false,
)

data class TransferProgress(
    val bytesDone: Long,
    val bytesTotal: Long,
    val filesDone: Int,
    val filesTotal: Int,
)

data class TransferSummary(
    /** Items left out, relative to what was transferred, with the reason why. */
    val skipped: List<String> = emptyList(),
)

private const val SEPARATOR = '/'

fun joinRemotePath(directory: String, name: String): String =
    if (directory.endsWith(SEPARATOR)) "$directory$name" else "$directory$SEPARATOR$name"

/** The folder holding [path], or null at the root. */
fun parentRemotePath(path: String): String? {
    val trimmed = path.trimEnd(SEPARATOR)
    if (trimmed.isEmpty()) return null
    val index = trimmed.lastIndexOf(SEPARATOR)
    return when {
        index < 0 -> null
        index == 0 -> SEPARATOR.toString()
        else -> trimmed.substring(0, index)
    }
}

/** False for names a server could send to reach outside the listed folder. Deleting `..` would delete the parent. */
fun isSafeRemoteName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && SEPARATOR !in name && '\u0000' !in name

private val WINDOWS_RESERVED_NAME = Regex("(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$")
private const val WINDOWS_UNSAFE_CHARACTERS = "\\:*?\"<>|"

/**
 * True when [name] is safe to create on any desktop. A valid name on Linux can be a path (`a\b`), an alternate data
 * stream (`a:b`) or a device (`CON`) on Windows, and a name can end in a space or dot that Windows silently drops.
 */
fun isSafeLocalName(name: String): Boolean =
    isSafeRemoteName(name) &&
        name.none { it in WINDOWS_UNSAFE_CHARACTERS || it.isISOControl() } &&
        !name.endsWith('.') && !name.endsWith(' ') &&
        !WINDOWS_RESERVED_NAME.matches(name)

/**
 * [text] with what could disguise it replaced: control characters could break the layout, and bidirectional overrides
 * can make `exe.txt` look like `txt.exe`. For showing only, since a real name has to reach the server unchanged.
 */
fun printableRemoteText(text: String): String = text.map { char ->
    if (char.isISOControl() || char in '\u200B'..'\u200F' || char in '\u202A'..'\u202E' ||
        char in '\u2066'..'\u2069' || char == '\u061C' || char == '\uFEFF'
    ) {
        '\uFFFD'
    } else {
        char
    }
}.joinToString("")

/** [name], or `name (1).ext` and so on: the first one that isn't taken. */
inline fun uniqueFileName(name: String, isTaken: (String) -> Boolean): String {
    if (!isTaken(name)) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    var number = 1
    while (true) {
        val candidate = "${name.substring(0, dot)} ($number)${name.substring(dot)}"
        if (!isTaken(candidate)) return candidate
        number++
    }
}
