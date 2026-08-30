@file:Suppress("detekt.style.ReturnCount")

package com.sshellspace.feature.terminal.data

import java.io.File

fun isDefaultShell(shellCommand: String?, isWindows: Boolean): Boolean {
    if (shellCommand.isNullOrBlank()) return true
    val default = if(isWindows) resolveWindowsDefault() else resolveUnixDefault()
    return sameExecutable(shellCommand, default, isWindows)
}

private fun sameExecutable(a: String, b: String, isWindows: Boolean): Boolean {
    val nameA = File(a).name
    val nameB = File(b).name
    return if (isWindows) {
        nameA.equals(nameB, ignoreCase = true)
    } else {
        nameA == nameB
    }
}

private fun resolveUnixDefault(): String {
    System.getenv("SHELL")?.takeIf { it.isNotBlank() }?.let { return it }

    runCatching {
        val uid = ProcessBuilder("id", "-un").start()
            .inputStream.bufferedReader().readText().trim()
        val passwdLine = ProcessBuilder("getent", "passwd", uid).start()
            .inputStream.bufferedReader().readText().trim()
        passwdLine.substringAfterLast(':').takeIf { it.isNotBlank() }
    }.getOrNull()?.let { return it }

    return "/bin/bash"
}

private fun resolveWindowsDefault(): String {
    findOnPath("pwsh.exe")?.let { return it }
    findOnPath("powershell.exe")?.let { return it }
    return System.getenv("ComSpec")?.takeIf { it.isNotBlank() } ?: "cmd.exe"
}

private fun findOnPath(executable: String): String? {
    val pathEnv = System.getenv("PATH") ?: return null
    for (dir in pathEnv.split(File.pathSeparatorChar)) {
        val candidate = File(dir, executable)
        if (candidate.isFile) return candidate.absolutePath
    }
    return null
}
