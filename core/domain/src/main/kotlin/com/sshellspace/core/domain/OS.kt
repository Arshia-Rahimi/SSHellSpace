package com.sshellspace.core.domain

enum class OS {
    Linux,
    MacOS,
    Windows,
    Unknown;
}

fun getOs(): OS {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("linux") -> OS.Linux
        osName.contains("mac") -> OS.MacOS
        osName.contains("win") -> OS.Windows
        else -> OS.Unknown
    }
}
