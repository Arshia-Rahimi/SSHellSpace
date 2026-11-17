package com.sshellspace.feature.terminal.ui.screen.emulators.jediterm

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import com.sshellspace.core.domain.models.OS
import com.sshellspace.core.domain.models.getOs
import com.sshellspace.feature.terminal.domain.SshShellHandle
import com.sshellspace.feature.terminal.domain.models.Shell
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val READ_BUFFER_CHARS = 8192
private const val INITIAL_COLUMNS = 80
private const val INITIAL_ROWS = 24

/** The program a JediTerm terminal shows: a shell channel on an SSH session, or a shell in a local PTY. */
internal interface ShellProcess {
    /** The next output, or null once the program has ended. */
    suspend fun read(): String?
    suspend fun write(bytes: ByteArray)
    suspend fun resize(columns: Int, rows: Int)
    suspend fun waitForExit(): Int
    suspend fun close()
}

internal class SshShellProcess(private val shell: SshShellHandle) : ShellProcess {
    override suspend fun read(): String? = shell.read()
    override suspend fun write(bytes: ByteArray) = shell.writeBytes(bytes)
    override suspend fun resize(columns: Int, rows: Int) = shell.resize(columns, rows)
    override suspend fun waitForExit(): Int = shell.waitForExit()
    override suspend fun close() = shell.close()
}

internal class PtyShellProcess(shell: Shell) : ShellProcess {
    private val process: PtyProcess = PtyProcessBuilder(localCommand(shell))
        .setEnvironment(localEnvironment(shell))
        .setDirectory(shell.workingDirectory ?: System.getProperty("user.home"))
        .setInitialColumns(INITIAL_COLUMNS)
        .setInitialRows(INITIAL_ROWS)
        .start()

    // A reader keeps multi-byte UTF-8 characters intact across read boundaries.
    private val output = InputStreamReader(process.inputStream, Charsets.UTF_8)

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val buffer = CharArray(READ_BUFFER_CHARS)
        // The PTY reports an error rather than the end of the stream on some systems once the shell has exited.
        val count = runCatching { output.read(buffer) }.getOrDefault(-1)
        if (count < 0) null else String(buffer, 0, count)
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        process.outputStream.write(bytes)
        process.outputStream.flush()
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        if (process.isAlive) process.winSize = WinSize(columns, rows)
    }

    override suspend fun waitForExit(): Int = withContext(Dispatchers.IO) { process.waitFor() }

    override suspend fun close() = withContext(Dispatchers.IO) {
        process.destroy()
    }
}

private val isWindows = getOs() == OS.Windows
private val isMac = getOs() == OS.MacOS

/** The command line for [shell], with the device's own shell standing in when it names none. */
private fun localCommand(shell: Shell): Array<String> {
    val command = shell.command?.takeIf { it.isNotBlank() }
        ?: if (isWindows) {
            System.getenv("ComSpec")?.takeIf { it.isNotBlank() } ?: "cmd.exe"
        } else {
            System.getenv("SHELL")?.takeIf { File(it).canExecute() } ?: "/bin/sh"
        }
    // On macOS an app started from the Dock gets a bare PATH, so shells start as login shells there, as in
    // Terminal.app, and read the profile that sets it up.
    val arguments = shell.arguments.ifEmpty { if (isMac) listOf("-l") else emptyList() }
    return arrayOf(command) + arguments
}

private fun localEnvironment(shell: Shell): Map<String, String> = buildMap {
    putAll(System.getenv())
    put("TERM", "xterm-256color")
    put("COLORTERM", "truecolor")
    // Apps opened from the Dock or Finder get no locale, and shells then mangle anything that isn't ASCII.
    if (!isWindows && get("LANG").isNullOrBlank() && get("LC_ALL").isNullOrBlank()) put("LANG", "en_US.UTF-8")
    putAll(shell.environment)
}
