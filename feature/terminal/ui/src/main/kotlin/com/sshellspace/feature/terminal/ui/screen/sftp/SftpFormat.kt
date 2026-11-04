package com.sshellspace.feature.terminal.ui.screen.sftp

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
