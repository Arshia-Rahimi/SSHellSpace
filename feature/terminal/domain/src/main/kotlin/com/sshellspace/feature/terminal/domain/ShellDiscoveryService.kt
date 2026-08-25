package com.sshellspace.feature.terminal.domain

interface ShellDiscoveryService {
    fun discover(): List<Shell>
}
