package com.sshellspace.core.domain.models

enum class OS {
    Linux,
    MacOS,
    Windows,
    Unknown;
}

fun getOs(): OS = System.getProperty("os.name").lowercase()
    .run {
        when {
            contains("linux") -> OS.Linux
            contains("mac") -> OS.MacOS
            contains("win") -> OS.Windows
            else -> OS.Unknown
        }
    }

