package com.sshellspace.feature.terminal.data

import com.sshellspace.core.domain.models.OS
import com.sshellspace.core.domain.models.getOs
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import java.io.File

class ShellDiscoveryServiceImpl : ShellDiscoveryService {
    override fun discover(): List<Shell> = when (getOs()) {
        OS.Windows -> discoverWindowsShells() + discoverWslShells()
        else -> discoverUnix()
    }

    private fun discoverUnix(): List<Shell> {
        val shellsFile = File("/etc/shells")

        if (!shellsFile.isFile) return emptyList()

        return shellsFile
            .readLines()
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { File(it).isFile && File(it).canExecute() }
            .map { path ->
                Shell(
                    name = File(path).name,
                    command = path,
                    isDeviceDefault = isDefaultShell(path, false),
                )
            }
            .distinctBy { it.command }
            .toList()
    }

    private fun discoverWindowsShells(): List<Shell> {
        val candidates = listOf(
            "cmd.exe",
            "powershell.exe",
            "pwsh.exe",
            "bash.exe",
            "zsh.exe",
            "fish.exe",
        )

        return candidates
            .mapNotNull { executable ->
                findOnPath(executable)?.let { path ->
                    Shell(
                        name = executable.removeSuffix(".exe"),
                        command = path,
                        isDeviceDefault = isDefaultShell(path, false),
                    )
                }
            }
            .distinctBy { it.command }
    }

    fun discoverWslShells(): List<Shell> {
        val wsl = findOnPath("wsl.exe") ?: return emptyList()

        val distributions = ProcessBuilder(wsl, "--list", "--quiet")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return distributions.map { distro ->
            Shell(
                name = "WSL ($distro)",
                command = "$wsl -d $distro",
            )
        }
    }

    private fun findOnPath(executable: String): String? {
        val path = System.getenv("PATH") ?: return null

        return path
            .split(File.pathSeparator)
            .asSequence()
            .map { File(it, executable) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }
}
