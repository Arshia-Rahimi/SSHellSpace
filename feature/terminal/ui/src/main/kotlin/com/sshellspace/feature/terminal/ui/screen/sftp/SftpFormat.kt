package com.sshellspace.feature.terminal.ui.screen.sftp

import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val BYTES_PER_UNIT = 1024.0
private val SIZE_UNITS = listOf("KB", "MB", "GB", "TB", "PB")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val MODE_LETTERS = "rwxrwxrwx"

internal fun formatSize(bytes: Long): String {
    if (bytes < BYTES_PER_UNIT) return "$bytes B"
    var value = bytes.toDouble()
    var unit = -1
    while (value >= BYTES_PER_UNIT && unit < SIZE_UNITS.lastIndex) {
        value /= BYTES_PER_UNIT
        unit++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, SIZE_UNITS[unit])
}

internal fun formatModified(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
    millis?.let { DATE_FORMAT.format(Instant.ofEpochMilli(it).atZone(zone)) }.orEmpty()

/** Mode bits the way `ls -l` shows them, such as `rwxr-xr-x`. */
internal fun formatMode(mode: Int?): String = mode?.let {
    MODE_LETTERS.mapIndexed { index, letter ->
        if (it and (1 shl (MODE_LETTERS.lastIndex - index)) != 0) letter else '-'
    }.joinToString("")
}.orEmpty()

/** What the status bar says about the folder, given what the list [visible] shows and has [selected]. */
internal fun SftpBrowserState.summary(visible: List<RemoteFile>, selected: List<RemoteFile>): String {
    if (selected.isNotEmpty()) {
        // The size a server gives a folder says nothing about what's in it, so a folder alone has no size to show.
        val bytes = selected.filter { it.kind == RemoteFileKind.File }.sumOf { it.size }
        val size = if (bytes > 0) ", ${formatSize(bytes)}" else ""
        return "${selected.size} of ${visible.size} selected$size"
    }
    val hidden = if (showHidden) 0 else entries.count { it.isDotfile }
    return when {
        filter.isNotEmpty() -> "${visible.size} of ${entries.size - hidden} items"
        hidden > 0 -> "${itemCount(visible.size)}, $hidden hidden"
        else -> itemCount(visible.size)
    }
}

private fun itemCount(count: Int) = if (count == 1) "1 item" else "$count items"

/** This computer's own files are copied to and from another folder, rather than downloaded and uploaded. */
internal val SftpBrowserState.downloadLabel get() = if (isThisDevice) "Copy to…" else "Download…"
internal val SftpBrowserState.uploadLabel get() = if (isThisDevice) "Copy here" else "Upload"
internal val SftpBrowserState.uploadFilesLabel get() = if (isThisDevice) "Copy files here…" else "Upload files…"
internal val SftpBrowserState.uploadFolderLabel get() = if (isThisDevice) "Copy a folder here…" else "Upload a folder…"
