package com.sshellspace.feature.terminal.domain

import com.sshellspace.feature.terminal.domain.models.Shell

interface ShellDiscoveryService {
    fun discover(): List<Shell>
}
